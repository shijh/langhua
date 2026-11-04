/*!
 * niimbot.js — 浏览器直连精臣（NIIMBOT）D11 热敏标签机，只做一件事：把一张 1 位位图打出来。
 *
 * 为什么是浏览器而不是服务端：Windows 上的 Java 没有能用的 BLE 库（BlueCove 早就停在经典蓝牙、
 * 不支持 BLE），而 Chrome / Edge 的 Web Bluetooth 用的就是本机蓝牙。所以「本机蓝牙」这条路走浏览器：
 * 页面把标签画成位图 → 直接写 GATT 特征值 → 打印机吐纸，OFBiz 一行 Java 都不用加。
 *
 * 协议是社区逆向出来的（见 AGENTS.md Step 261 的出处）。用到的那几条：
 *
 *   GATT   service e7810a71-73ae-499d-8c15-faa9aef0c3f2
 *          characteristic bef8d6c9-9c21-4c9e-b632-bd58c1009f9f（WRITE_NO_RESPONSE + NOTIFY）
 *   帧     55 55 cmd len ...data crc AA AA，crc = cmd ^ len ^ 每个 data 字节
 *   连接   整帧外面再套一个 0x03：03 55 55 C1 01 01 C1 AA AA
 *   打印   0x23 标签类型 → 0x21 浓度 → 0x01 开始 → 0x20 允许清除 → 0x03 开页 →
 *          0x13 页面尺寸(行,列) → 0x15 份数 → 每行一个 0x85 → 0xE3 关页 →
 *          0xA3 轮询进度到 page == 份数 → 0xF3 结束
 *   行     0x85 数据 = 行号(2,大端) + 0x00 + 总黑点数(2,大端) + 重复次数(1) + 位图(stride 字节，
 *          高位在左，1 = 黑)。**这是 total 模式**（见 rowFrames 的注释），D11 打印头 96 点
 *          （12mm × 8 点/mm）所以 stride = 12。
 *
 * 这份按两代机器写：**D11 / D110 / B1 / B21**（203 dpi、D11 打印头 96 点 = 12mm 胶带、
 * B1 384 点 ≈ 48mm）走老流程（`0x01` 开始 1 字节 + `0x03` 开页）；
 * **B1 Pro / M2-H**（300 dpi、打印头 567 点 ≈ 48mm）走 v4 流程（`0x01` 开始打印 9 个字节、
 * 没有 `0x03` 开页）。机型由页面上的「纸型」决定（`options.family` 是 `v1` 还是 `v4`），
 * 因为同一台电脑上两台机器都可能连着。
 *
 * 没有第三方依赖，纯 ES5 + Promise；用 `node --check` 与假 BLE 设备单测（%TEMP%\jup\js-check）。
 */
(function (root) {
    'use strict';

    var SERVICE_UUID = 'e7810a71-73ae-499d-8c15-faa9aef0c3f2';
    var CHAR_UUID = 'bef8d6c9-9c21-4c9e-b632-bd58c1009f9f';
    /* 打印机的蓝牙名：D11 叫 D11_xxxx，D110 叫 D110-xxxx，B1/B2 同族，一起列上方便挑设备 */
    var NAMES = ['D1', 'D11', 'D110', 'B1', 'B2'];
    /** 开页后的头几行慢一点发（打印机还没站好，一口气灌会把最上面几行吞掉） */
    var WARM_UP_ROWS = 16;
    var WARM_UP_PACE_MS = 30;
    var CMD = {
        START_PRINT: 0x01,
        START_PAGE: 0x03,
        SET_DIMENSION: 0x13,
        SET_QUANTITY: 0x15,
        ALLOW_PRINT_CLEAR: 0x20,
        SET_DENSITY: 0x21,
        SET_LABEL_TYPE: 0x23,
        INFO: 0x40,
        PRINT_ROW: 0x85,
        PRINT_STATUS: 0xA3,
        STATUS_DATA: 0xA5,
        CONNECT: 0xC1,
        HEARTBEAT: 0xDC,
        END_PAGE: 0xE3,
        END_PRINT: 0xF3
    };

    /* 打印机对请求的应答码：默认是 +1，少数几条 +16（协议里 respoffset） */
    function replyOf(cmd, offset) {
        return (cmd + (offset || 1)) & 0xFF;
    }

    /** 帧校验：命令 ^ 长度 ^ 每一个数据字节。 */
    function crcOf(cmd, data) {
        var crc = cmd ^ (data.length & 0xFF);
        for (var i = 0; i < data.length; i++) {
            crc ^= data[i] & 0xFF;
        }
        return crc & 0xFF;
    }

    /** 组一帧：[55 55 cmd len ...data crc AA AA]。 */
    function frame(cmd, data) {
        var bytes = data || [];
        var out = new Uint8Array(bytes.length + 7);
        out[0] = 0x55;
        out[1] = 0x55;
        out[2] = cmd & 0xFF;
        out[3] = bytes.length & 0xFF;
        for (var i = 0; i < bytes.length; i++) {
            out[4 + i] = bytes[i] & 0xFF;
        }
        out[4 + bytes.length] = crcOf(cmd, out.subarray(4, 4 + bytes.length));
        out[5 + bytes.length] = 0xAA;
        out[6 + bytes.length] = 0xAA;
        return out;
    }

    /**
     * 把位图切成一行一个 0x85 包。
     *
     * `bits` 是 `width * height` 个 0/1（1 = 打黑），行优先；位图里高位在左（0x80 是最左边那一点）。
     *
     * 行包的数据是 **6 个字节的头 + 位图**：
     *
     *   [行号高, 行号低, 0x00, 总黑点数(低), 总黑点数(高), 重复次数, ...stride 字节位图]
     *
     * 这是 `iscarelli/niimbot-web-bluetooth` 的 `docs/protocol-v4.md` 里写的 **total 模式**
     * （原文：both tasks use total mode），也是 niim.blue 实际发出去的字节：
     * 那一行到底多黑，只报**一个 16 位的总数**，不再是三段各报一个。
     *
     * **别改回“三段各一个计数”**：那种四字节写法看着也能印出东西，但打印机把多出来的 9 个字节
     * 当成了位图的头 —— 每一行的像素都往左错 72 点，而且 `run`（重复次数）被读成黑点数，
     * 同一行重复几十次。2026-11-04 的 B1 真机上印出来就是一条巨大的黑块。
     *
     * @param {Uint8Array} bits 0/1 位图
     * @param {number} width 宽（点）
     * @param {number} height 高（行）
     * @return {Uint8Array[]} 每一行的整帧
     */
    function rowFrames(bits, width, height) {
        var stride = Math.ceil(width / 8);
        var out = [];
        for (var y = 0; y < height; y++) {
            var row = new Uint8Array(stride);
            var total = 0;
            for (var x = 0; x < width; x++) {
                if (bits[y * width + x]) {
                    row[x >> 3] |= 0x80 >> (x & 7);
                    total++;
                }
            }
            var data = [y >> 8, y & 0xFF, 0x00, total & 0xFF, (total >> 8) & 0xFF, 1];
            for (var i = 0; i < stride; i++) {
                data.push(row[i]);
            }
            out.push(frame(CMD.PRINT_ROW, data));
        }
        return out;
    }

    function sleep(ms) {
        return new Promise(function (resolve) {
            setTimeout(resolve, ms);
        });
    }

    function hex(bytes) {
        var text = '';
        for (var i = 0; i < bytes.length; i++) {
            text += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16);
        }
        return text;
    }

    /** 这台浏览器能不能用蓝牙：要有 Web Bluetooth，且页面是安全上下文（https 或 localhost）。 */
    function supported() {
        return typeof root.navigator !== 'undefined'
            && !!root.navigator.bluetooth
            && root.isSecureContext !== false;
    }

    /** 不能用的原因，直接给页面显示（中文）。 */
    function whyNotSupported() {
        if (typeof root.navigator === 'undefined' || !root.navigator.bluetooth) {
            return '这个浏览器没有蓝牙接口：请用电脑上的 Chrome 或 Edge 打开本页'
                + '（手机自带浏览器、微信内置浏览器、Firefox、Safari 都不支持 Web Bluetooth）。';
        }
        if (root.isSecureContext === false) {
            return '这个地址不是安全上下文，浏览器不给用蓝牙。请改用 http://localhost:端口 打开'
                + '（自签证书的 https 被浏览器当成不安全），例如 http://localhost:8090/mfg/control/…';
        }
        return '';
    }

    /**
     * 一台打印机。
     *
     * @param {function} log 进度回调，收一行中文
     */
    function Driver(log) {
        this.log = log || function () {};
        this.device = null;
        this.characteristic = null;
        this.buffer = [];
        this.pending = {};
        this.watchers = [];
        this.onFrame = null;
    }

    Driver.prototype.supported = supported;
    Driver.prototype.whyNotSupported = whyNotSupported;

    /**
     * 判断这个错误是不是“通道没接上”。
     *
     * Chrome 在 GATT 没连住的时候抛的就是这两句：
     * `GATT Server is disconnected. Cannot retrieve services. (Re)connect first with device.gatt.connect.`
     * 或者 `NetworkError: GATT operation already in progress.` / `… failed for unknown reason`。
     * 这类都能靠“断干净再连一次”解决，别的错（没权限、设备被移除）重试也没用。
     */
    function looksDisconnected(error) {
        var text = String((error && error.message) || error || '');
        return /GATT Server is disconnected|NetworkError|disconnected|Connection.*(fail|closed)|No Services/i
            .test(text);
    }

    /**
     * 连上 GATT 并拿到打印特征值，**接不上就断干净重连**（最多三次）。
     *
     * 真机上最常见的情形是打印机已经被别的中心连着（手机里的精臣 App、另一个标签页，
     * 或者上一轮点完没释放）：`gatt.connect()` 会**先 resolve**、紧接着链路就被对端断掉，
     * 于是随后的 `getPrimaryService` 报 "GATT Server is disconnected"。只连一次就会把这种
     * 很快就好的情况当成失败，所以这里要收尾重连。
     */
    Driver.prototype.openGatt = function (left) {
        var self = this;
        left = (left === undefined) ? 3 : left;
        var gatt = self.device.gatt;
        return gatt.connect().then(function (server) {
            if (!gatt.connected) {
                throw new Error('GATT Server is disconnected.');
            }
            return server.getPrimaryService(SERVICE_UUID);
        }).then(function (service) {
            return service.getCharacteristic(CHAR_UUID);
        }).catch(function (error) {
            if (left <= 1 || !looksDisconnected(error)) {
                throw error;
            }
            self.log('蓝牙通道没接住（' + ((error && error.message) || error) + '），断开重连一次…');
            try {
                gatt.disconnect();
            } catch (ignore) {
                // 本来就断了
            }
            return sleep(600).then(function () {
                return self.openGatt(left - 1);
            });
        });
    };

    /** 弹系统蓝牙选择框、连 GATT、打开通知，并发送协议里的"连接"包。 */
    Driver.prototype.connect = function (options) {
        var self = this;
        options = options || {};
        if (!supported()) {
            return Promise.reject(new Error(whyNotSupported()));
        }
        var filters = (options.names || NAMES).map(function (prefix) {
            return { namePrefix: prefix };
        });
        self.log('正在查找打印机（系统会弹出蓝牙设备列表）…');
        return root.navigator.bluetooth.requestDevice({
            filters: filters,
            optionalServices: [SERVICE_UUID]
        }).then(function (device) {
            self.device = device;
            self.log('已选择：' + (device.name || '(没有名字)'));
            // 链路被对端断开时（手机 App 抢走、打印机休眠）把特征值清掉，
            // 不然页面以为还连着，下一次点打印直接往一个死通道上写
            if (!self.disconnectWatched && device.addEventListener) {
                self.disconnectWatched = true;
                device.addEventListener('gattserverdisconnected', function () {
                    self.characteristic = null;
                    self.log('打印机断开了蓝牙连接（可能被手机 App 或别的标签页占着）');
                });
            }
            return self.openGatt();
        }).then(function (characteristic) {
            self.characteristic = characteristic;
            characteristic.addEventListener('characteristicvaluechanged', function (event) {
                self.receive(event.target.value);
            });
            return characteristic.startNotifications();
        }).then(function () {
            // 连接包：整帧外面再套一个 0x03，全协议只有这一条这样发。
            // **只发这一条**：以前这里写完之后又用 request() 发了一次空数据的 0xC1，
            // 打印机等于收到两个连接包（第二个还是 len=0 的畸形包），后面的命令就没人理了。
            var raw = prefixedConnect();
            self.log('连接包 ' + hex(raw));
            return self.sendConnect(raw).then(function () {
                // 连上以后让打印机喘一口气，紧接着灌命令容易被整条丢掉
                return sleep(250);
            });
        }).then(function () {
            return self.hello();
        }).then(function () {
            return self;
        }).catch(function (error) {
            if (!looksDisconnected(error)) {
                throw error;
            }
            // Chrome 的原话（“…… (Re)connect first with `device.gatt.connect`”）对着工位上的人说不通
            throw new Error('连不上打印机的蓝牙通道：打印机可能正被手机里的 App 或另一个标签页连着，'
                + '或者刚上电还没准备好。把那边断开、等两秒再点一次。'
                + '（浏览器原话：' + ((error && error.message) || error) + '）');
        });
    };

    function connectFrame() {
        return frame(CMD.CONNECT, [0x01]);
    }

    function prefixedConnect() {
        var one = connectFrame();
        var raw = new Uint8Array(one.length + 1);
        raw[0] = 0x03;
        raw.set(one, 1);
        return raw;
    }

    /**
     * 发连接包并等 `0xC2`；没应答就原样再发一次（有的固件第一次会丢）。
     * 两次都不应答也不算失败——后面能打印就行。
     *
     * @param {Uint8Array} raw 带 `0x03` 前缀的连接帧
     * @return {Promise} 结束即 resolve
     */
    Driver.prototype.sendConnect = function (raw) {
        var self = this;
        var once = function () {
            // 先挂上监听再写，免得应答比 write 的 Promise 还快
            var waiting = self.expect(0xC2, 2500);
            return self.write(raw).then(function () {
                return waiting;
            });
        };
        var answerOf = function (frameSeen) {
            // frameSeen 是 [命令, 数据]；这里要的是数据第一个字节（0x01 = 连接成功）
            var payload = frameSeen[1];
            return payload.length ? payload[0] : '?';
        };
        return once().then(function (frameSeen) {
            self.log('打印机已应答连接（' + answerOf(frameSeen) + '）');
        }, function () {
            self.log('第一次连接包没应答，再发一次');
            return once().then(function (frameSeen) {
                self.log('打印机已应答连接（' + answerOf(frameSeen) + '）');
            }, function () {
                self.log('连接包始终没有应答（不致命，继续）');
            });
        });
    };

    /**
     * 连接后的握手。
     *
     * B1 那一代不带这一套就"收命令但不打印"，D11 的老固件又不需要它——所以全部**尽力而为**：
     * 每条等 900ms，没人应答就跳过，绝不因此让打印失败。
     */
    Driver.prototype.hello = function () {
        var self = this;
        var steps = [
            [CMD.STATUS_DATA, [0x01], '状态 0xA5'],
            [CMD.HEARTBEAT, [0x04], '心跳 0xDC[04]'],
            [CMD.HEARTBEAT, [0x03], '心跳 0xDC[03]']
        ];
        var chain = Promise.resolve();
        steps.forEach(function (step) {
            chain = chain.then(function () {
                if (self.silentHello) {
                    return null;      // 第一条就不答，这一代机器不认这套，别再一条条白等
                }
                // 先挂监听、再发：先等的写法会让每条都白等满 900ms
                var waiting = self.expectAny([replyOf(step[0], 16), 0xB5, 0xD9, 0xDD, 0xDE, 0xDF], 700);
                return self.write(frame(step[0], step[1])).then(function () {
                    return waiting;
                }).then(function (frameSeen) {
                    self.log('握手 ' + step[2] + ' → ' + hex([frameSeen[0]])
                        + ' ' + hex(frameSeen[1].slice(0, 12)));
                }, function () {
                    if (!self.silentHello) {
                        self.silentHello = true;
                        self.log('这台机器不回应握手（真机 D11 就是这样），后面的不再等');
                    }
                }).then(function () {
                    return sleep(40);
                });
            });
        });
        return chain;
    };

    /** 发一帧（分片按 MTU），不等应答。 */
    Driver.prototype.write = function (bytes) {
        var characteristic = this.characteristic;
        if (!characteristic) {
            return Promise.reject(new Error('还没连上打印机'));
        }
        var view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
        if (characteristic.writeValueWithoutResponse) {
            return characteristic.writeValueWithoutResponse(view);
        }
        return characteristic.writeValue(view);
    };

    /** 收通知：把字节攒进缓冲，按 55 55 … AA AA 切帧，交给等待中的请求。 */
    Driver.prototype.receive = function (dataView) {
        var bytes = new Uint8Array(dataView.buffer, dataView.byteOffset, dataView.byteLength);
        for (var i = 0; i < bytes.length; i++) {
            this.buffer.push(bytes[i]);
        }
        for (;;) {
            if (this.buffer.length < 5) {
                return;
            }
            if (this.buffer[0] !== 0x55 || this.buffer[1] !== 0x55) {
                this.buffer.shift();
                continue;
            }
            var length = this.buffer[3];
            var total = length + 7;
            if (this.buffer.length < total) {
                return;
            }
            var frameBytes = this.buffer.slice(0, total);
            this.buffer = this.buffer.slice(total);
            if (frameBytes[total - 2] !== 0xAA || frameBytes[total - 1] !== 0xAA) {
                continue;
            }
            var cmd = frameBytes[2];
            var payload = frameBytes.slice(4, 4 + length);
            // 打印机说的每一句都记下来：排查时这行日志最有用
            this.log('← ' + hex([cmd]) + (payload.length ? ' ' + hex(payload.slice(0, 32))
                + (payload.length > 32 ? ' …(' + payload.length + ' 字节)' : '') : ''));
            var waiting = this.pending[cmd];
            if (waiting) {
                delete this.pending[cmd];
                waiting(payload);
            }
            if (this.watchers.length) {
                this.watchers = this.watchers.filter(function (watcher) {
                    if (watcher.cmds.indexOf(cmd) < 0) {
                        return true;
                    }
                    watcher.done([cmd, payload]);
                    return false;
                });
            }
            if (this.onFrame) {
                this.onFrame(cmd, payload);
            }
        }
    };

    /**
     * 等某几个应答码里的任意一个，拿到 `[命令, 数据]`。
     *
     * 用「先挂监听、再写」的写法，免得应答比 write 的 Promise 还快。
     *
     * @param {number[]} cmds 要等的应答码
     * @param {number} timeoutMs 超时就 reject
     * @return {Promise} resolve([cmd, payload])
     */
    Driver.prototype.expectAny = function (cmds, timeoutMs) {
        var self = this;
        var wait = timeoutMs || 1500;
        return new Promise(function (resolve, reject) {
            var watcher = { cmds: cmds, done: null };
            var timer = setTimeout(function () {
                self.watchers = self.watchers.filter(function (one) {
                    return one !== watcher;
                });
                reject(new Error('等 ' + cmds.map(function (cmd) {
                    return hex([cmd]);
                }).join('/') + ' 超时（' + wait + 'ms）'));
            }, wait);
            watcher.done = function (frameSeen) {
                clearTimeout(timer);
                resolve(frameSeen);
            };
            self.watchers.push(watcher);
        });
    };

    /** 等一个应答码。 */
    Driver.prototype.expect = function (cmd, timeoutMs) {
        return this.expectAny([cmd], timeoutMs);
    };

    /** 发一条要应答的命令。 */
    Driver.prototype.request = function (cmd, data, reply, timeoutMs) {
        var self = this;
        var waiting = self.expect(reply, timeoutMs);
        return self.write(frame(cmd, data)).then(function () {
            return waiting;
        });
    };

    /**
     * 发一条**不指望应答**的命令：收不到就记一句、接着走。
     *
     * 真机实测（D11-B3180030，2026-11-02）：只答连接包，`0x23` 起的设置命令全不回。
     * 卡在这里一点意义没有，所以设置类命令一律走这条路；真的没打出来，最后的进度轮询会说。
     *
     * @param {number} cmd 命令
     * @param {number[]} data 数据
     * @param {number[]} replies 可能收到的应答码
     * @param {string} label 日志里的名字
     * @return {Promise} 永远 resolve（带上是否应答了）
     */
    Driver.prototype.soft = function (cmd, data, replies, label) {
        var self = this;
        if (self.silentSetup >= 2) {
            // 前面已经两条没应答了：不再每条白等，直接发
            return self.write(frame(cmd, data)).then(function () {
                self.log(label + ' 已发送（前两条都没应答，不再等）');
                return sleep(40).then(function () {
                    return false;
                });
            });
        }
        // 已经知道它不爱应答之后，剩下的命令只给很短的窗口
        var window = self.silentSetup >= 1 ? 500 : 1200;
        var waiting = self.expectAny(replies, window);
        return self.write(frame(cmd, data)).then(function () {
            return waiting;
        }).then(function (frameSeen) {
            self.log(label + ' 已应答 ' + hex([frameSeen[0]])
                + (frameSeen[1].length ? '（' + hex(frameSeen[1].slice(0, 6)) + '）' : ''));
            return true;
        }, function () {
            self.silentSetup = (self.silentSetup || 0) + 1;
            self.log(label + ' 没有应答（继续）');
            return false;
        }).then(function (answered) {
            // 命令之间留一点空隙，BLE 上一口气灌容易丢
            return sleep(40).then(function () {
                return answered;
            });
        });
    };

    /**
     * 打印一张位图。
     *
     * @param {Uint8Array} bits 0/1 位图（1 = 黑），宽度必须等于这张纸的横向点数
     *        （D11 12mm = 96 点，B1 40mm = 472 点）
     * @param {number} width 宽（点）
     * @param {number} height 高（行）
     * @param {object} options density（1~3，默认 2）、copies（默认 1）、paceMs（每行之间的停顿，
     *        默认 10）、family（`d11` 缺省 = 老 D11 那一套；`b1` = B1 / B21；`v4` = B1 Pro / M2）
     * @return {Promise} 打完 resolve
     */
    Driver.prototype.print = function (bits, width, height, options) {
        var self = this;
        options = options || {};
        var density = options.density || 2;
        var copies = options.copies || 1;
        var pace = options.paceMs == null ? 10 : options.paceMs;
        var v4 = options.family === 'v4';
        /* b1 = B1 / B21（协议 3、203dpi）：开始打印 7 字节、尺寸 6 字节带份数、不发 0x20 / 0x15。
           d11（`options.family` 缺省）= 老 D11 那一套 1 字节开始 + 4 字节尺寸，已经印对过，不要动。 */
        var b1 = options.family === 'b1';
        /* 开页后先停一下、前几行慢发（避免最上面几行被吞）；真机排查时可以用 slowStart:false 关掉 */
        var slowStart = options.slowStart !== false;
        var rows = rowFrames(bits, width, height);
        var step = function (cmd, data, replies, label) {
            return self.soft(cmd, data, replies, label);
        };
        self.log('打印流程：'
            + (v4 ? 'v4（B1 Pro / M2，300 dpi，开始 9 字节）'
                : b1 ? 'b1（B1 / B21，开始 7 字节 + 尺寸 6 字节）'
                    : 'D11 那种（开始 1 字节）')
            + '，行包 total 模式，' + height + ' 行 × ' + width + ' 点');
        return Promise.resolve().then(function () {
            return step(CMD.SET_LABEL_TYPE, [0x01], [0x33], '标签类型（有间隙）');
        }).then(function () {
            return step(CMD.SET_DENSITY, [density & 0xFF], [0x31], '浓度 ' + density);
        }).then(function () {
            if (v4) {
                // v4（B1 Pro / M2）的 PrintStart 要 9 个字节：前面是页数，后面是这一代固件要的
                // 保留位与速度。只发 1 个字节（D11 那样）它不认这条命令。
                return step(CMD.START_PRINT,
                    [0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00], [0x02], '开始打印（v4 9 字节）');
            }
            if (b1) {
                // b1（B1 / B21，协议 3）的 PrintStart 是 **7 个字节**：`[页数高, 页数低, 0,0,0,0,0]`。
                // 只发 1 个字节时这台 B1 照样应答，但页数读到的是垃圾值
                // （真机 2026-11-04：发 1 字节 → 空跑了 5 张纸）。
                return step(CMD.START_PRINT,
                    [copies >> 8, copies & 0xFF, 0, 0, 0, 0, 0], [0x02],
                    '开始打印（b1 7 字节，页数 ' + copies + '）');
            }
            return step(CMD.START_PRINT, [0x01], [0x02], '开始打印');
        }).then(function () {
            if (b1) {
                // b1 的流程里没有「允许清除缓冲」这一步（协议文档的 b1 序列）
                return null;
            }
            return step(CMD.ALLOW_PRINT_CLEAR, [0x01], [0x30], '允许清除缓冲');
        }).then(function () {
            if (v4) {
                // v4 没有「开页」这一步，页面尺寸就是这一张的全部
                return null;
            }
            return step(CMD.START_PAGE, [0x01], [0x04], '开页');
        }).then(function () {
            /* 尺寸是 行在前、列在后（协议里 SetPageSize 的第一个参数是 feed 方向）。
               b1 那份是 **6 个字节**：`[H_hi, H_lo, W_hi, W_lo, 0x00, 份数]`——最后两位是份数，
               所以 b1 不再单独发 `0x15`。 */
            var data = [height >> 8, height & 0xFF, width >> 8, width & 0xFF];
            if (b1) {
                data.push(0x00, copies & 0xFF);
            }
            return step(CMD.SET_DIMENSION, data, [0x14],
                '页面 ' + height + ' 行 × ' + width + ' 点');
        }).then(function () {
            if (b1) {
                return null;      // 份数已经在尺寸那一条里了
            }
            return step(CMD.SET_QUANTITY, [copies >> 8, copies & 0xFF], [0x16], '份数 ' + copies);
        }).then(function () {
            self.log('发送位图：' + rows.length + ' 行（每行 ' + rows[0].length + ' 字节的包）…');
            /* 刚开页的头几条行包容易被打印机吞掉（真机上最上面 12 行没印出来），
               所以先等它站好，前十几行慢一点发 */
            return slowStart ? sleep(150) : null;
        }).then(function () {
            var chain = Promise.resolve();
            rows.forEach(function (one, index) {
                chain = chain.then(function () {
                    return self.write(one);
                }).then(function () {
                    var wait = pace;
                    if (slowStart && index < WARM_UP_ROWS && pace < WARM_UP_PACE_MS) {
                        wait = WARM_UP_PACE_MS;
                    }
                    return wait > 0 ? sleep(wait) : null;
                });
            });
            return chain;
        }).then(function () {
            // 先挂上“这一页印完了”的监听再关页：真机在关页后会主动发 0xE0（0001），
            // 而 0xA3 那个进度查询它根本不答
            self.pageDone = self.expectAny([0xE0], 4000);
            self.pageDone.catch(function () {
                return null;
            });
            return step(CMD.END_PAGE, [0x01], [0xE4], '关页');
        }).then(function (pageAccepted) {
            return self.waitForPrint(copies).then(function (reached) {
                return reached || pageAccepted;
            });
        }).then(function (ok) {
            return step(CMD.END_PRINT, [0x01], [0xF4], '结束').then(function () {
                return ok;
            });
        }).then(function (ok) {
            if (ok) {
                self.log('打印完成');
                return { printed: true };
            }
            self.log('位图已经全部发给打印机，但它一条回执都没有：看纸出没出来，'
                + '没有的话把上面的日志发我');
            return { printed: false };
        });
    };

    /**
     * 等这一页印完。
     *
     * 真机（D11-B3180030，2026-11-02）实测：`0xA3` 进度查询**一次都不回**，但关页后会主动发
     * `0xE0 0001`（这一页印完）。所以先等那条事件，没等到再退回去轮询 `0xA3`，两边都没反应
     * 就快速收工（不再白等十几秒）。
     *
     * @param {number} copies 份数
     * @return {Promise} resolve(true) 表示确实打到份数了
     */
    Driver.prototype.waitForPrint = function (copies) {
        var self = this;
        var done = self.pageDone || Promise.reject(new Error('没有关页事件'));
        done.catch(function () {
            return null;
        });
        return done.then(function () {
            self.log('打印机报：这一页印完了');
            return true;
        }, function () {
            var deadline = Date.now() + (self.printTimeoutMs || 5000);
            var last = -1;
            var misses = 0;
            var poll = function () {
                if (Date.now() > deadline || misses >= 2) {
                    if (last >= 0) {
                        self.log('进度只报到 ' + last + ' / ' + copies);
                    }
                    return Promise.resolve(last >= copies);
                }
                return self.request(CMD.PRINT_STATUS, [0x01], 0xB3, 1200).then(function (frameSeen) {
                    var payload = frameSeen[1];
                    var page = payload.length >= 2 ? ((payload[0] << 8) | payload[1]) : 0;
                    misses = 0;
                    if (page !== last) {
                        last = page;
                        self.log('打印进度：' + page + ' / ' + copies);
                    }
                    if (page >= copies) {
                        return true;
                    }
                    return sleep(150).then(poll);
                }).catch(function () {
                    misses++;
                    return sleep(200).then(poll);
                });
            };
            return poll();
        });
    };

    Driver.prototype.disconnect = function () {
        this.characteristic = null;
        this.pending = {};
        this.watchers = [];
        this.buffer = [];
        if (this.device && this.device.gatt && this.device.gatt.connected) {
            this.device.gatt.disconnect();
        }
        this.device = null;
    };

    /**
     * 诊断：只问不打印，把打印机说的每一句都记进日志。
     *
     * 打印机型号很多、固件差得很远（这台 D11-B3180030 就只答连接包），
     * 遇到打不出来时先跑这个：把 `←` 开头的行发出去，就能知道该用哪一套流程。
     *
     * @return {Promise} resolve 收集到的应答码数组
     */
    Driver.prototype.diagnose = function () {
        var self = this;
        var heard = [];
        var probes = [
            [CMD.STATUS_DATA, [0x01], [0xB5], '状态 0xA5[01]'],
            [CMD.HEARTBEAT, [0x04], [0xD9, 0xDD, 0xDE, 0xDF], '心跳 0xDC[04]'],
            [CMD.HEARTBEAT, [0x03], [0xD9, 0xDD, 0xDE, 0xDF], '心跳 0xDC[03]（打印头宽度）'],
            [CMD.INFO, [0x08], [0x48], '型号 0x40[08]'],
            [CMD.INFO, [0x01], [0x41], '浓度 0x40[01]'],
            [CMD.INFO, [0x0B], [0x4B], '序列号 0x40[0b]'],
            [CMD.INFO, [0x09], [0x49], '固件版本 0x40[09]'],
            [0x1A, [0x01], [0x1B], '耗材 0x1A[01]']
        ];
        self.log('诊断：只问不打印，每条等 1.2 秒');
        var chain = Promise.resolve();
        probes.forEach(function (probe) {
            chain = chain.then(function () {
                var waiting = self.expectAny(probe[2], 1200);
                return self.write(frame(probe[0], probe[1])).then(function () {
                    return waiting;
                }).then(function (frameSeen) {
                    heard.push(frameSeen[0]);
                    self.log('诊断 ' + probe[3] + ' → ' + hex([frameSeen[0]]) + ' '
                        + hex(frameSeen[1].slice(0, 20)));
                }, function () {
                    self.log('诊断 ' + probe[3] + ' 没有应答');
                }).then(function () {
                    return sleep(60);
                });
            });
        });
        return chain.then(function () {
            self.log(heard.length
                ? '诊断结束：打印机答了 ' + heard.map(function (cmd) { return hex([cmd]); }).join(' / ')
                : '诊断结束：除了连接包，打印机一个字都没回（把上面整段发给我）');
            return heard;
        });
    };

    /** 连过的打印机名（页面上显示一下，免得人不知道现在连的是哪台）。 */
    Driver.prototype.deviceName = function () {
        return this.device ? (this.device.name || '(没有名字)') : '';
    };

    root.Niimbot = {
        SERVICE_UUID: SERVICE_UUID,
        CHAR_UUID: CHAR_UUID,
        CMD: CMD,
        crcOf: crcOf,
        frame: frame,
        rowFrames: rowFrames,
        hex: hex,
        supported: supported,
        whyNotSupported: whyNotSupported,
        Driver: Driver
    };
})(typeof window !== 'undefined' ? window : globalThis);
