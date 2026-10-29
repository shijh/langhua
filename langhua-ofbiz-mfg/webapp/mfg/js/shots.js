/*
  A part picture that the shop never stored (an order imported without one, or a pointer whose file
  was deleted) answers 404. Left alone the browser paints a broken image icon in the middle of a
  table, which reads as "the whole picture feature is broken" - so the picture takes itself off the
  page instead.

  The listener is registered in the capture phase because the error event of an <img> does not bubble.
  Pages that list parts hide such pointers before they are rendered (MfgStockServices
  .dropMissingPictures), this is the safety net for the pointers that are already in the database.
*/
document.addEventListener('error', function (event) {
    var img = event.target;
    if (!img || img.tagName !== 'IMG' || !img.className) {
        return;
    }
    if (String(img.className).split(/\s+/).indexOf('shot') < 0) {
        return;
    }
    img.className = img.className + ' gone';
    if (img.parentNode && img.parentNode.tagName === 'A') {
        // the link would open nothing at all
        img.parentNode.className = img.parentNode.className + ' gone';
    }
}, true);
