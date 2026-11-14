(function() {
    var TEXT_RE = /(chapter|chap\.?|ch\.?|episode|ep\.?|ตอนที่|ตอน|capítulo|capitulo|cap\.?|bab|глава|第|話|话|章|화)\s*[#\d.]/i;
    var HREF_RE = /(chapter|chap|episode|ep|ตอน|capitulo|bab)[-_\/.=]?\d/i;

    function noHash(u) { return (u || '').split('#')[0]; }

    var links = document.querySelectorAll('a[href]');
    var entries = [];
    var seen = {};
    for (var i = 0; i < links.length; i++) {
        var a = links[i];
        if (a.closest && a.closest('nav,header,footer')) continue;
        var href = a.getAttribute('href') || '';
        if (!href || href.charAt(0) === '#' || href.toLowerCase().indexOf('javascript:') === 0) continue;
        var abs;
        try { abs = noHash(new URL(href, document.baseURI).href); } catch (e) { continue; }
        if (!/^https?:/i.test(abs) || seen[abs]) continue;
        var text = (a.innerText || a.textContent || a.getAttribute('title') || '').replace(/\s+/g, ' ').trim();
        var path = '';
        try { path = new URL(abs).pathname; } catch (e2) {}
        if (!TEXT_RE.test(text) && !HREF_RE.test(path)) continue;
        if (!text) text = decodeURIComponent(path.split('/').filter(function(x) { return x; }).pop() || abs);
        seen[abs] = true;
        entries.push({ t: text.slice(0, 90), h: abs, el: a });
    }

    if (entries.length >= 3) {
        var counts = new Map();
        for (var k = 0; k < entries.length; k++) {
            var node = entries[k].el.parentElement;
            var depth = 0;
            while (node && node !== document.body && node !== document.documentElement && depth < 8) {
                counts.set(node, (counts.get(node) || 0) + 1);
                node = node.parentElement;
                depth++;
            }
        }
        var bestNode = null, bestCount = 0;
        counts.forEach(function(count, el) {
            if (count > bestCount || (count === bestCount && bestNode && bestNode.contains(el))) {
                bestCount = count;
                bestNode = el;
            }
        });
        if (bestNode && bestCount >= 2 && bestCount >= entries.length * 0.5) {
            entries = entries.filter(function(en) { return bestNode.contains(en.el); });
        }
    }

    return JSON.stringify({
        title: document.title || '',
        url: location.href,
        chapters: entries.slice(0, 2000).map(function(en) { return { t: en.t, h: en.h }; })
    });
})();
