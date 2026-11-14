(function() {
    var LAZY_ATTRS = ['data-src', 'data-original', 'data-lazy-src', 'data-lazy', 'data-url', 'data-echo', 'data-img', 'data-hi-res-src'];
    var LAZY_SRCSET_ATTRS = ['data-srcset', 'data-lazy-srcset'];
    var IMAGE_EXT = /\.(jpe?g|png|webp|gif|avif|bmp)(\?|#|$)/i;
    var SKIP_ANCESTORS = 'nav,header,footer,aside,[role="navigation"],[role="banner"],[role="complementary"],[role="contentinfo"]';
    var SKIP_NAV = '#comments,.comments,.comment,.comment-list,[id*="comment"],aside,.sidebar,.widget,.related,.recommend';
    var SKIP_HINT = /(^|[\s_-])(logo|icon|avatar|sprite|emoji|badge|favicon|ads?|advert|banner|pixel|tracking)([\s_-]|$)/i;
    var MIN_SIDE = 150;
    var MIN_AREA = 40000;

    var NEXT_WORDS = ['next', 'siguiente', 'suivant', 'próximo', 'proximo', 'selanjutnya', 'berikutnya', 'lanjut', 'далее', 'следующ', 'weiter', 'nächste', 'volgende', '下一', '下页', '次の', '次へ', '다음', 'ต่อไป', 'ถัดไป', '›', '»', '→', '>>', '▶', '►'];
    var PREV_WORDS = ['prev', 'previous', 'anterior', 'précédent', 'precedent', 'sebelumnya', 'sebelum', 'назад', 'предыдущ', 'zurück', 'vorherige', '上一', '前の', '前へ', '이전', 'ก่อนหน้า', 'ย้อนกลับ', '‹', '«', '←', '<<', '◀', '◄'];
    var HOME_STRONG = ['chapter list', 'chapters list', 'all chapters', 'table of contents', 'manga info', 'series', 'ตอนทั้งหมด', 'รายการตอน', 'daftar chapter', 'daftar', 'список глав', '目录', '目錄', '一覧', '목록'];
    var HOME_WEAK = ['home', 'index', 'toc', 'หน้าหลัก', 'главная'];
    var CHAPTER_HINT = /(chapter|chap\b|ch\.|episode|\bep\b|ตอน|capítulo|capitulo|\bbab\b|глава|第|話|话|章|화)/i;

    function norm(s) { return (s || '').replace(/\s+/g, ' ').trim().toLowerCase(); }

    function absolute(u) {
        if (!u) return '';
        u = String(u).trim();
        if (!u) return '';
        if (u.indexOf('blob:') === 0) return '';
        if (u.indexOf('data:') === 0) return u.length > 2000 ? u : '';
        if (u.toLowerCase().indexOf('javascript:') === 0) return '';
        try { return new URL(u, document.baseURI).href; } catch (e) { return ''; }
    }

    function noHash(u) { return (u || '').split('#')[0]; }

    function bestFromSrcset(srcset) {
        if (!srcset) return '';
        var best = '', bestScore = -1;
        var parts = String(srcset).split(',');
        for (var i = 0; i < parts.length; i++) {
            var seg = parts[i].trim().split(/\s+/);
            if (!seg[0]) continue;
            var score = 1;
            if (seg[1]) {
                var n = parseFloat(seg[1]);
                if (!isNaN(n)) score = n;
            }
            if (score >= bestScore) { bestScore = score; best = seg[0]; }
        }
        return best;
    }

    function hasLazy(img) {
        var i;
        for (i = 0; i < LAZY_ATTRS.length; i++) if (img.getAttribute(LAZY_ATTRS[i])) return true;
        for (i = 0; i < LAZY_SRCSET_ATTRS.length; i++) if (img.getAttribute(LAZY_SRCSET_ATTRS[i])) return true;
        return false;
    }

    function pickSource(img) {
        var anchor = img.closest ? img.closest('a[href]') : null;
        if (anchor && IMAGE_EXT.test(anchor.getAttribute('href') || '')) {
            var full = absolute(anchor.getAttribute('href'));
            if (full) return full;
        }
        var i, v;
        for (i = 0; i < LAZY_SRCSET_ATTRS.length; i++) {
            v = bestFromSrcset(img.getAttribute(LAZY_SRCSET_ATTRS[i]));
            if (v) { v = absolute(v); if (v) return v; }
        }
        for (i = 0; i < LAZY_ATTRS.length; i++) {
            v = absolute(img.getAttribute(LAZY_ATTRS[i]));
            if (v) return v;
        }
        v = absolute(img.currentSrc);
        if (v) return v;
        v = absolute(bestFromSrcset(img.getAttribute('srcset')));
        if (v) return v;
        return absolute(img.getAttribute('src'));
    }

    function tooSmall(img) {
        var lazy = hasLazy(img);
        var w = lazy ? 0 : (img.naturalWidth || 0);
        var h = lazy ? 0 : (img.naturalHeight || 0);
        if (!w || !h) {
            var aw = parseInt(img.getAttribute('width'), 10) || 0;
            var ah = parseInt(img.getAttribute('height'), 10) || 0;
            if (aw && ah) { w = aw; h = ah; }
        }
        if (!w || !h) {
            if (lazy) return false;
            var r = img.getBoundingClientRect();
            if (r.width > 1 && r.height > 1) { w = r.width; h = r.height; }
        }
        if (!w || !h) return false;
        return w < MIN_SIDE || h < MIN_SIDE || (w * h) < MIN_AREA;
    }

    function hinted(img) {
        var cn = (typeof img.className === 'string') ? img.className : '';
        return SKIP_HINT.test(cn + ' ' + (img.id || ''));
    }

    var imgs = document.querySelectorAll('img');
    var entries = [];
    var seen = {};
    for (var i = 0; i < imgs.length; i++) {
        var img = imgs[i];
        if (img.closest && img.closest(SKIP_ANCESTORS)) continue;
        if (hinted(img)) continue;
        if (tooSmall(img)) continue;
        var url = pickSource(img);
        if (!url || seen[url]) continue;
        seen[url] = true;
        entries.push({ url: url, el: img });
    }

    if (entries.length >= 4) {
        var counts = new Map();
        for (var e = 0; e < entries.length; e++) {
            var node = entries[e].el.parentElement;
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
        if (bestNode && bestCount >= 3 && bestCount >= entries.length * 0.5) {
            entries = entries.filter(function(en) { return bestNode.contains(en.el); });
        }
    }

    var lastImg = entries.length ? entries[entries.length - 1].el : null;
    var currentUrl = noHash(location.href);

    function matchWords(text, words) {
        for (var k = 0; k < words.length; k++) {
            var w = words[k];
            if (w.length <= 2) {
                if (text.length <= 4 && text.indexOf(w) >= 0) return true;
            } else if (text.indexOf(w) >= 0) {
                return true;
            }
        }
        return false;
    }

    function labelOf(el) {
        var parts = [el.innerText || el.textContent || '', el.getAttribute('aria-label') || '', el.getAttribute('title') || '', el.getAttribute('alt') || ''];
        var inner = el.querySelector ? el.querySelector('img[alt]') : null;
        if (inner) parts.push(inner.getAttribute('alt') || '');
        return norm(parts.join(' '));
    }

    function hintOf(el) {
        var cn = (typeof el.className === 'string') ? el.className : '';
        return norm((el.id || '') + ' ' + cn + ' ' + (el.getAttribute('data-action') || ''));
    }

    function isVisible(el) {
        if (el.tagName === 'LINK') return true;
        return el.getClientRects().length > 0;
    }

    function isDisabled(el) {
        if (el.disabled) return true;
        if (el.getAttribute('aria-disabled') === 'true') return true;
        var cn = (typeof el.className === 'string') ? el.className : '';
        return /(^|\s)disabled(\s|$)/i.test(cn);
    }

    function cssPath(el) {
        var parts = [];
        while (el && el.nodeType === 1 && el !== document.documentElement) {
            if (el.id && /^[A-Za-z][\w-]*$/.test(el.id) && document.querySelectorAll('#' + el.id).length === 1) {
                parts.unshift('#' + el.id);
                break;
            }
            var n = 1, sib = el;
            while ((sib = sib.previousElementSibling)) { if (sib.tagName === el.tagName) n++; }
            parts.unshift(el.tagName.toLowerCase() + ':nth-of-type(' + n + ')');
            el = el.parentElement;
        }
        return parts.join('>');
    }

    function targetOf(el) {
        var href = el.getAttribute('href');
        var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('title') || '').replace(/\s+/g, ' ').trim().slice(0, 60);
        if (href && href.charAt(0) !== '#' && href.toLowerCase().indexOf('javascript:') !== 0) {
            var abs = noHash(absolute(href));
            if (!abs || !/^https?:/i.test(abs) || abs === currentUrl) return null;
            return { href: abs, text: txt };
        }
        if (el.tagName === 'LINK') return null;
        return { clickPath: cssPath(el), text: txt };
    }

    function navScore(el, type) {
        var lab = labelOf(el), txt = norm(el.innerText || el.textContent || ''), hint = hintOf(el);
        var s = 0;
        var relTokens = norm(el.getAttribute('rel')).split(' ');
        var wanted = type === 'next' ? ['next'] : ['prev', 'previous'];
        for (var k = 0; k < wanted.length; k++) if (relTokens.indexOf(wanted[k]) >= 0) s += 100;
        var hintRe = type === 'next' ? /(^|[^a-z])next([^a-z]|$)/ : /(^|[^a-z])prev(ious)?([^a-z]|$)/;
        if (hintRe.test(hint)) s += 40;
        if (lab.length <= 100 && matchWords(lab, type === 'next' ? NEXT_WORDS : PREV_WORDS)) {
            s += 30;
            if (txt.length <= 24) s += 10;
        }
        if (s === 0) return 0;
        if (CHAPTER_HINT.test(lab)) s += 20;
        if (/page|หน้า|halaman/.test(lab) && !CHAPTER_HINT.test(lab)) s -= 15;
        return s;
    }

    var cands = document.querySelectorAll('a[href], button, [role="button"], [onclick], link[rel~="next"], link[rel~="prev"], link[rel~="previous"]');

    function pickNav(type) {
        var best = null, bestScore = 0;
        for (var c = 0; c < cands.length; c++) {
            var el = cands[c];
            if (el.closest && el.closest(SKIP_NAV)) continue;
            if (isDisabled(el) || !isVisible(el)) continue;
            var sc = navScore(el, type);
            if (sc < 30) continue;
            if (lastImg && (lastImg.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING)) sc += 5;
            if (el.tagName !== 'A' && el.tagName !== 'LINK' && !el.getAttribute('href')) sc -= 5;
            var t = targetOf(el);
            if (!t) continue;
            if (sc > bestScore) { best = t; bestScore = sc; }
        }
        return best;
    }

    function pickHome() {
        var best = null, bestScore = 0;
        var bc = document.querySelectorAll('.breadcrumb a[href], .breadcrumbs a[href], [class*="breadcrumb"] a[href], nav[aria-label*="readcrumb" i] a[href]');
        for (var b = bc.length - 1; b >= 0; b--) {
            var bt = targetOf(bc[b]);
            if (bt && bt.href) { best = bt; bestScore = 60; break; }
        }
        var links = document.querySelectorAll('a[href]');
        for (var l = 0; l < links.length; l++) {
            var a = links[l];
            if (a.closest && a.closest(SKIP_NAV)) continue;
            var lab = labelOf(a), txt = norm(a.innerText || a.textContent || ''), hint = hintOf(a);
            if (txt.length > 50 && lab.length > 50) continue;
            var s = 0;
            if (matchWords(lab, HOME_STRONG)) s += 45;
            else if (matchWords(lab, HOME_WEAK)) s += 15;
            if (/(^|[^a-z])(index|toc|series|all-chapters|chapter-list)([^a-z]|$)/.test(hint)) s += 20;
            if (s < 15 || !isVisible(a)) continue;
            var t = targetOf(a);
            if (!t || !t.href) continue;
            if (s > bestScore) { best = t; bestScore = s; }
        }
        if (!best) {
            try {
                var u = new URL(currentUrl);
                var segs = u.pathname.split('/').filter(function(x) { return x; });
                if (segs.length >= 2) {
                    segs.pop();
                    u.pathname = '/' + segs.join('/') + '/';
                    u.search = '';
                    best = { href: u.href, text: '' };
                }
            } catch (err) {}
        }
        return best;
    }

    return JSON.stringify({
        title: document.title || '',
        url: location.href,
        images: entries.map(function(en) { return en.url; }),
        nav: { next: pickNav('next'), prev: pickNav('prev'), home: pickHome() }
    });
})();
