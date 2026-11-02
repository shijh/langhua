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
 *   行     0x85 数据 = 行号(2,大端) + 三段 4 字节的黑点数(3) + 重复次数(1) + 位图(stride 字节，
 *          高位在左，1 = 黑)。D11 打印头 96 点（12mm × 8 点/mm），所以 stride = 12。
 *
 * 这份只按 **D11 / D110 这一条（203 dpi、96 点）** 写：D11 的兄弟型号（D11_H、B1 Pro）是 300 dpi、
 * 144/567 点，用另一套（v4）流程，不在本文件范围内。
 *
 * 没有第三方依赖，纯 ES5 + Promise；用 `node --check` 与假 BLE 设备单测（%TEMP%\jup\js-check）。
 */
(function (root) {
    'use strict';

    var SERVICE_UUID = 'e7810a71-73ae-499d-8c15-faa9aef0c3f2';
    var CHAR_UUID = 'bef8d6c9-9c21-4c9e-b632-bd58c1009f9f';
    /* 打印机的蓝牙名：D11 叫 D11_xxxx，D110 叫 D110-xxxx，B1/B2 同族，一起列上方便挑设备 */
    var NAMES = ['D1', 'D11', 'D110', 'B1', 'B2'];

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

    /** 一个字节里几个 1（行包里要报"这一行印多少个黑点"，按 4 字节一段分三段报）。 */
    function blackBitsIn(byte) {
        var n = 0;
        for (var i = 0; i < 8; i++) {
            if ((byte >> i) & 1) {
                n++;
            }
        }
        return n;
    }

    /**
     * 把位图切成一行一个 0x85 包。
     *
     * `bits` 是 `width * height` 个 0/1（1 = 打黑），行优先。D11 一行 96 点 = 12 字节，
     * 正好三段 4 字节，所以黑点数是 [c0, c1, c2]；位图里高位在左（0x80 是最左边那一点）。
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
            for (var x = 0; x < width; x++) {
                if (bits[y * width + x]) {
                    row[x >> 3] |= 0x80 >> (x & 7);
                }
            }
            var count0 = 0;
            var count1 = 0;
            var count2 = 0;
            for (var i = 0; i < stride; i++) {
                var n = blackBitsIn(row[i]);
                if (i < 4) {
                    count0 += n;
                } else if (i < 8) {
                    count1 += n;
                } else {
                    count2 += n;
                }
            }
            var data = [y >> 8, y & 0xFF, count0, count1, count2, 1];
            for (i = 0; i < stride; i++) {
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
        this.onFrame = null;
    }

    Driver.prototype.supported = supported;
    Driver.prototype.whyNotSupported = whyNotSupported;

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
            return device.gatt.connect();
        }).then(function (server) {
            return server.getPrimaryService(SERVICE_UUID);
        }).then(function (service) {
            return service.getCharacteristic(CHAR_UUID);
        }).then(function (characteristic) {
            self.characteristic = characteristic;
            characteristic.addEventListener('characteristicvaluechanged', function (event) {
                self.receive(event.target.value);
            });
            return characteristic.startNotifications();
        }).then(function () {
            // 连接包：整帧外面还套一个 0x03，是协议里唯一这样发的一条
            var connect = frame(CMD.CONNECT, [0x01]);
            var raw = new Uint8Array(connect.length + 1);
            raw[0] = 0x03;
            raw.set(connect, 1);
            self.log('连接包 ' + hex(raw));
            return self.write(raw).then(function () {
                return self.request(CMD.CONNECT, [], 0xC2, 1500).then(function (payload) {
                    self.log('打印机已应答连接（' + (payload.length ? payload[0] : '?') + '）');
                });
            }).catch(function () {
                // 有的固件不回这条，但连接本身是通的：继续
                self.log('没有等到连接应答（不致命，继续）');
            });
        }).then(function () {
            return self.hello();
        }).then(function () {
            return self;
        });
    };

    /**
     * 连接后的握手。
     *
     * B1 那一代不带这一套就"收命令但不打印"，D11 的老固件又不需要它——所以全部**尽力而为**：
     * 每条等 400ms，没人应答就跳过，绝不因此让打印失败。
     */
    Driver.prototype.hello = function () {
        var self = this;
        var steps = [
            [CMD.STATUS_DATA, [0x01], replyOf(CMD.STATUS_DATA, 16)],
            [CMD.HEARTBEAT, [0x04], 0xD9]
        ];
        var chain = Promise.resolve();
        steps.forEach(function (step) {
            chain = chain.then(function () {
                return self.request(step[0], step[1], step[2], 400).then(function (payload) {
                    self.log('握手 ' + hex([step[0]]) + ' → ' + hex(payload.slice(0, 12)));
                }).catch(function () {
                    self.log('握手 ' + hex([step[0]]) + ' 没有应答（跳过）');
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
            var waiting = this.pending[cmd];
            if (waiting) {
                delete this.pending[cmd];
                waiting(payload);
            }
            if (this.onFrame) {
                this.onFrame(cmd, payload);
            }
        }
    };

    /** 发一条要应答的命令，等 reply（超时 reject，但**不**抛到打印流程外面去吓人）。 */
    Driver.prototype.request = function (cmd, data, reply, timeoutMs) {
        var self = this;
        var wait = timeoutMs || 1500;
        return new Promise(function (resolve, reject) {
            var timer = setTimeout(function () {
                delete self.pending[reply];
                reject(new Error('等 ' + hex([reply]) + ' 超时（' + wait + 'ms）'));
            }, wait);
            self.pending[reply] = function (payload) {
                clearTimeout(timer);
                resolve(payload);
            };
            self.write(frame(cmd, data)).catch(function (error) {
                clearTimeout(timer);
                delete self.pending[reply];
                reject(error);
            });
        });
    };

    /**
     * 打印一张位图。
     *
     * @param {Uint8Array} bits 0/1 位图（1 = 黑），宽度必须等于打印头点数（D11 = 96）
     * @param {number} width 宽（点）
     * @param {number} height 高（行）
     * @param {object} options density（1~3，默认 2）、copies（默认 1）、paceMs（每行之间的停顿，默认 10）
     * @return {Promise} 打完 resolve
     */
    Driver.prototype.print = function (bits, width, height, options) {
        var self = this;
        options = options || {};
        var density = options.density || 2;
        var copies = options.copies || 1;
        var pace = options.paceMs == null ? 10 : options.paceMs;
        var rows = rowFrames(bits, width, height);
        var step = function (cmd, data, reply, label) {
            return self.request(cmd, data, reply, 3000).then(function (payload) {
                self.log(label + ' 已应答' + (payload.length ? '（' + hex(payload.slice(0, 4)) + '）' : ''));
            });
        };
        return Promise.resolve().then(function () {
            return step(CMD.SET_LABEL_TYPE, [0x01], 0x33, '标签类型（有间隙）');
        }).then(function () {
            return step(CMD.SET_DENSITY, [density & 0xFF], 0x31, '浓度 ' + density);
        }).then(function () {
            return step(CMD.START_PRINT, [0x01], 0x02, '开始打印');
        }).then(function () {
            return step(CMD.ALLOW_PRINT_CLEAR, [0x01], 0x30, '允许清除缓冲');
        }).then(function () {
            return step(CMD.START_PAGE, [0x01], 0x04, '开页');
        }).then(function () {
            // 尺寸是 行在前、列在后（协议里 SetPageSize 的第一个参数是 feed 方向）
            return step(CMD.SET_DIMENSION,
                [height >> 8, height & 0xFF, width >> 8, width & 0xFF], 0x14,
                '页面 ' + height + ' 行 × ' + width + ' 点');
        }).then(function () {
            return step(CMD.SET_QUANTITY, [copies >> 8, copies & 0xFF], 0x16, '份数 ' + copies);
        }).then(function () {
            self.log('发送位图：' + rows.length + ' 行（每行 ' + rows[0].length + ' 字节的包）…');
            var chain = Promise.resolve();
            rows.forEach(function (one) {
                chain = chain.then(function () {
                    return self.write(one);
                }).then(function () {
                    return pace > 0 ? sleep(pace) : null;
                });
            });
            return chain;
        }).then(function () {
            return step(CMD.END_PAGE, [0x01], 0xE4, '关页');
        }).then(function () {
            return self.waitForPrint(copies);
        }).then(function () {
            return step(CMD.END_PRINT, [0x01], 0xF4, '结束');
        }).then(function () {
            self.log('打印完成');
        });
    };

    /**
     * 轮询打印进度，直到 `page` 到份数。
     *
     * **这一步不能省**：不等它就把 0xF3 发过去，纸会停在一半（社区里踩过）。
     * 应答 `>HBB`：page(u16 大端)、进度、走纸进度。
     */
    Driver.prototype.waitForPrint = function (copies) {
        var self = this;
        var deadline = Date.now() + (self.printTimeoutMs || 25000);
        var last = -1;
        var poll = function () {
            if (Date.now() > deadline) {
                self.log('等打印完成超时（' + copies + ' 份只到 ' + last + '），仍然结束这一单');
                return Promise.resolve();
            }
            return self.request(CMD.PRINT_STATUS, [0x01], 0xB3, 1500).then(function (payload) {
                var page = payload.length >= 2 ? ((payload[0] << 8) | payload[1]) : 0;
                if (page !== last) {
                    last = page;
                    self.log('打印进度：' + page + ' / ' + copies);
                }
                if (page >= copies) {
                    return null;
                }
                return sleep(120).then(poll);
            }).catch(function () {
                return sleep(250).then(poll);
            });
        };
        return Promise.resolve(poll());
    };

    Driver.prototype.disconnect = function () {
        this.characteristic = null;
        this.pending = {};
        this.buffer = [];
        if (this.device && this.device.gatt && this.device.gatt.connected) {
            this.device.gatt.disconnect();
        }
        this.device = null;
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
