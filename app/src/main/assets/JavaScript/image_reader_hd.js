(function(items) {
    var st = { done: false, res: {} };
    window.__clintHd = st;
    var IMAGE_EXT = /\.(jpe?g|png|webp|gif|avif|bmp)(\?|#|$)/i;
    var SKIP_HINT = /(^|[\s_-])(logo|icon|avatar|sprite|emoji|badge|favicon|ads?|advert|banner|pixel|tracking|thumb|thumbs|thumbnail)([\s_-]|$)/i;
    var URL_SKIP = /(^|[\/_.-])(thumb|thumbs|thumbnail|avatar|logo|icon|sprite)([\/_.-]|$)/i;
    var MAIN_HINT = /(main|full|viewer|reader|photo|picture|page|image|img|content)/i;
    var LAZY = ['data-full', 'data-large', 'data-orig', 'data-original', 'data-src', 'data-lazy-src', 'data-lazy', 'data-url', 'data-echo', 'data-img', 'data-hi-res-src'];

    function abs(u, base) {
        if (!u) return '';
        u = String(u).trim();
        if (!u || u.indexOf('data:') === 0 || u.indexOf('blob:') === 0 || u.toLowerCase().indexOf('javascript:') === 0) return '';
        try { return new URL(u, base).href; } catch (e) { return ''; }
    }

    function bestSrcset(ss) {
        if (!ss) return '';
        var parts = String(ss).split(','), best = '', bs = -1;
        for (var i = 0; i < parts.length; i++) {
            var seg = parts[i].trim().split(/\s+/);
            if (!seg[0]) continue;
            var n = seg[1] ? parseFloat(seg[1]) : 1;
            if (isNaN(n)) n = 1;
            if (n >= bs) { bs = n; best = seg[0]; }
        }
        return best;
    }

    function srcOf(img, base) {
        for (var i = 0; i < LAZY.length; i++) {
            var a = abs(img.getAttribute(LAZY[i]), base);
            if (a) return a;
        }
        var s = abs(bestSrcset(img.getAttribute('srcset')), base);
        if (s) return s;
        return abs(img.getAttribute('src'), base);
    }

    function pick(doc, base) {
        var og = doc.querySelector('meta[property="og:image"],meta[name="og:image"],meta[name="twitter:image"]');
        var ogUrl = og ? abs(og.getAttribute('content'), base) : '';
        var imgs = doc.querySelectorAll('img');
        var best = '', bs = -1e9;
        for (var i = 0; i < imgs.length; i++) {
            var img = imgs[i];
            if (img.closest('nav,header,footer,aside,[role="navigation"]')) continue;
            var u = srcOf(img, base);
            if (!u) continue;
            var cls = ((typeof img.className === 'string') ? img.className : '') + ' ' + (img.id || '');
            var s = 0;
            if (SKIP_HINT.test(cls)) s -= 100;
            var w = parseInt(img.getAttribute('width'), 10) || 0;
            var h = parseInt(img.getAttribute('height'), 10) || 0;
            s += Math.min((w * h) / 10000, 200);
            if (MAIN_HINT.test(cls)) s += 30;
            if (IMAGE_EXT.test(u)) s += 10;
            if (ogUrl && u === ogUrl) s += 40;
            if (img.closest('#image-container,.image-container,#viewer,.viewer,figure,main,article,#content')) s += 15;
            if (URL_SKIP.test(u)) s -= 60;
            if (s > bs) { bs = s; best = u; }
        }
        if (best && bs > -50) return best;
        return ogUrl;
    }

    var idx = 0, active = 0, CONC = 4;

    function finish() {
        if (idx >= items.length && active === 0) st.done = true;
    }

    function run(item) {
        var ctrl = (typeof AbortController !== 'undefined') ? new AbortController() : null;
        var t = setTimeout(function() { if (ctrl) ctrl.abort(); }, 15000);
        fetch(item.u, { credentials: 'include', signal: ctrl ? ctrl.signal : undefined })
            .then(function(r) { if (!r.ok) throw new Error('http'); return r.text(); })
            .then(function(html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var u = pick(doc, item.u);
                if (u) st.res[item.i] = u;
            })
            .catch(function() {})
            .then(function() {
                clearTimeout(t);
                active--;
                pump();
            });
    }

    function pump() {
        while (active < CONC && idx < items.length) {
            active++;
            run(items[idx++]);
        }
        finish();
    }

    if (!items.length) { st.done = true; return 'empty'; }
    pump();
    return 'started';
})(%%PAGES%%)
