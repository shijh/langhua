###########################################################################
# This library is part of Taglib OpenCms module of Langhua
#
# Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3.0 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# For further information about OpenCms, please see the
# project website: http://www.opencms.org 
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
###########################################################################

function showHelpPicture(imgId, imgUri) {
    var elem = document.getElementById("helpPicture");
    elem.innerHTML = "<img src='" + imgUri + "'>";
    showHelpPictureX(imgId, "Picture");
}

function showHelpPictureX(id, helpId) {
    var text = document.getElementById("help" + helpId);
    if (undefined == text) {
        return;
    }

    if (text.style.visibility == "visible") {
        return;
    }

    // get the help icon element
    var icon = document.getElementById("img" + id);
    var xOffset = 8;
    if (icon == null) {
    	// no icon found, this is a combo help text
    	icon = document.getElementById(id);
    	xOffset = 50;
    }

    try {
        var selectY = showPictureElement(text, icon, xOffset, 8, false);
        // hideSelectBoxes(text, selectY);
    } catch (e)  {
    	// ignore
    }
}

function showPictureElement(elem, icon, xOffset, yOffset, alignToLeft) {
    if (elem.style.visibility != "visible" && icon) {
        var x = findPicturePosX(icon) + xOffset;
        var y = findPicturePosY(icon) + yOffset;
        var textHeight = elem.scrollHeight;
        var textWidth = elem.scrollWidth;
        var scrollSize = 20;
        var scrollTop = 0;
        var scrollLeft = 0;
        var clientHeight = 0;
        var clientWidth = 0;
        if (document.documentElement && (document.documentElement.scrollTop || document.documentElement.clientHeight)) {
            scrollTop = document.documentElement.scrollTop;
            scrollLeft = document.documentElement.scrollLeft;
            clientHeight = document.documentElement.clientHeight;
            clientWidth = document.documentElement.clientWidth;
        } else if (document.body) {
            scrollTop = document.body.scrollTop;
            scrollLeft = document.body.scrollLeft;
            clientHeight = document.body.clientHeight;
            clientWidth = document.body.clientWidth;
        }
        if ((y + textHeight) > (clientHeight + scrollTop)) {
            y = y - textHeight;
        }
        if (y < scrollTop) {
            y = (clientHeight + scrollTop) - (textHeight + scrollSize);
        }
        if (y < scrollTop) {
            y = scrollTop;
        }
        if ((x + textWidth) > (clientWidth + scrollLeft) || alignToLeft) {
            x = x - textWidth;
        }
        if (x < scrollLeft) {
            x = (clientWidth + scrollLeft) - (textWidth + scrollSize);
        }
        if (x < scrollLeft) {
            x = scrollLeft;
        }

        if (alignToLeft) {
        	x += xOffset;
        }

        elem.style.left = x + "px";
        elem.style.top =  y + "px";
        elem.style.border = "none";
        elem.style.background = "none";
        elem.style.visibility = "visible";
        return y;
    }
}

// finds the x position of an element
function findPicturePosX(obj) {
    var curleft = 0;
    if (obj && obj.offsetParent) {
        while (obj.offsetParent) {
            curleft += obj.offsetLeft - obj.scrollLeft;
            obj = obj.offsetParent;
        }
    } else if (obj && obj.x) {
        curleft += obj.x;
    }
    return curleft;
}

// finds the y position of an element
function findPicturePosY(obj) {
    var curtop = 0;
    if (obj && obj.offsetParent) {
        while (obj.offsetParent) {
            curtop += obj.offsetTop - obj.scrollTop;
            obj = obj.offsetParent;
        }
    } else if (obj && obj.y) {
        curtop += obj.y;
    }
    return curtop;
}

function hideHelpPicture() {
    var text = document.getElementById("helpPicture");
    try {
        text.style.visibility = "hidden";
    } catch (e)  {
    	// ignore
    }
}
