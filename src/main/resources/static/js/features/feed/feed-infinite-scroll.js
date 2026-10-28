/**
 * AloUTE — Feed infinite scroll
 *
 * Loads the next server-rendered feed fragment when the sentinel approaches the
 * viewport. The fragment endpoint owns pagination metadata in response headers,
 * so this module never defines its own page/offset contract.
 *
 * Frontend rule §28 requirements implemented here:
 * - only one load-more request in flight
 * - duplicate post IDs are never appended
 * - explicit end-of-list, loading and retry states
 * - stable post IDs are preserved in the DOM
 */
(function () {
    'use strict';

    if (typeof window !== 'undefined' && window.__alouteFeedInfiniteScrollInitialized) return;
    if (typeof window !== 'undefined') window.__alouteFeedInfiniteScrollInitialized = true;

    function onReady(callback) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', callback, { once: true });
        } else {
            callback();
        }
    }

    function toNonNegativeInt(value) {
        const parsed = parseInt(value, 10);
        return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
    }

    onReady(function () {
        const root = document.getElementById('feed-infinite-scroll');
        const container = document.getElementById('feed-posts-container');
        if (!root || !container) return;

        const loadingEl = document.getElementById('feed-loading-indicator');
        const errorEl = document.getElementById('feed-load-error');
        const errorMessageEl = document.getElementById('feed-load-error-message');
        const retryBtn = document.getElementById('btn-feed-retry');
        const loadMoreBtn = document.getElementById('btn-feed-load-more');
        const endEl = document.getElementById('feed-end-state');
        const sentinel = document.getElementById('feed-scroll-sentinel');

        const endpoint = root.getAttribute('data-feed-endpoint') || '/feed/posts';
        const totalPages = toNonNegativeInt(root.getAttribute('data-total-pages')) || 0;
        const seenPostIds = new Set(
            Array.from(container.querySelectorAll('.post-card[data-post-id]'))
                .map(function (card) { return card.getAttribute('data-post-id'); })
                .filter(Boolean)
        );

        let nextPage = toNonNegativeInt(root.getAttribute('data-next-page'));
        let hasNext = root.getAttribute('data-has-next') === 'true';
        let loading = false;
        let emptyPageLoads = 0;
        let observer = null;

        function setLoading(value) {
            loading = value;
            root.setAttribute('aria-busy', value ? 'true' : 'false');
            if (loadingEl) loadingEl.classList.toggle('d-none', !value);
            if (retryBtn) retryBtn.disabled = value;
            if (loadMoreBtn) loadMoreBtn.disabled = value;
        }

        function showError(message) {
            if (!errorEl) return;
            if (errorMessageEl) errorMessageEl.textContent = message;
            errorEl.classList.remove('d-none');
        }

        function hideError() {
            if (errorEl) errorEl.classList.add('d-none');
        }

        function errorVisible() {
            return !!errorEl && !errorEl.classList.contains('d-none');
        }

        function showEnd() {
            hasNext = false;
            hideError();
            if (loadingEl) loadingEl.classList.add('d-none');
            if (loadMoreBtn) loadMoreBtn.classList.add('d-none');
            if (endEl) endEl.classList.remove('d-none');
            if (observer) {
                observer.disconnect();
                observer = null;
            }
        }

        function updateControls() {
            if (endEl) endEl.classList.toggle('d-none', hasNext || loading);
            if (loadMoreBtn) {
                const fallbackMode = !observer;
                loadMoreBtn.classList.toggle('d-none', !fallbackMode || !hasNext || loading || errorVisible());
            }
        }

        function sentinelNearViewport() {
            if (!sentinel) return false;
            const rect = sentinel.getBoundingClientRect();
            const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
            return rect.top <= viewportHeight + 300;
        }

        function scheduleViewportFill() {
            if (!hasNext || loading || errorVisible()) return;
            window.requestAnimationFrame(function () {
                if (hasNext && !loading && !errorVisible() && sentinelNearViewport()) {
                    loadMore();
                }
            });
        }

        async function loadMore() {
            if (loading || !hasNext || nextPage === null) return;

            hideError();
            setLoading(true);
            const separator = endpoint.indexOf('?') === -1 ? '?' : '&';
            const url = endpoint + separator + 'page=' + encodeURIComponent(String(nextPage));

            try {
                const response = await fetch(url, {
                    method: 'GET',
                    credentials: 'same-origin',
                    headers: {
                        'Accept': 'text/html',
                        'X-Requested-With': 'XMLHttpRequest'
                    }
                });
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }

                const html = await response.text();
                const doc = new DOMParser().parseFromString(html, 'text/html');
                const fragment = doc.querySelector('[data-feed-page-fragment]');
                if (!fragment) {
                    throw new Error('MISSING_FEED_FRAGMENT');
                }

                const items = Array.from(fragment.children).filter(function (child) {
                    return child.classList && child.classList.contains('feed-post-item');
                });

                let appended = 0;
                items.forEach(function (item) {
                    const card = item.querySelector('.post-card[data-post-id]');
                    const postId = card ? card.getAttribute('data-post-id') : null;
                    if (!postId || seenPostIds.has(postId)) return;
                    seenPostIds.add(postId);
                    container.appendChild(document.importNode(item, true));
                    appended++;
                });

                const headerHasNext = response.headers.get('X-Feed-Has-Next');
                const headerNextPage = response.headers.get('X-Feed-Next-Page');
                hasNext = headerHasNext !== null
                    ? headerHasNext === 'true'
                    : (items.length > 0 && nextPage + 1 < totalPages);
                nextPage = headerNextPage !== null
                    ? toNonNegativeInt(headerNextPage)
                    : nextPage + 1;
                if (nextPage !== null) {
                    root.setAttribute('data-next-page', String(nextPage));
                }
                root.setAttribute('data-has-next', hasNext ? 'true' : 'false');

                if (!hasNext) {
                    showEnd();
                } else if (appended === 0) {
                    emptyPageLoads++;
                    if (emptyPageLoads >= 3) {
                        showError('Không thể tải thêm bài viết mới. Vui lòng thử lại.');
                    }
                } else {
                    emptyPageLoads = 0;
                }
            } catch (error) {
                showError('Không thể tải thêm bài viết. Vui lòng thử lại.');
            } finally {
                setLoading(false);
                updateControls();
                scheduleViewportFill();
            }
        }

        if (retryBtn) {
            retryBtn.addEventListener('click', function () {
                hideError();
                loadMore();
            });
        }

        if (loadMoreBtn) {
            loadMoreBtn.addEventListener('click', loadMore);
        }

        if (typeof window.IntersectionObserver === 'function' && sentinel) {
            observer = new IntersectionObserver(function (entries) {
                if (errorVisible()) return;
                for (let i = 0; i < entries.length; i++) {
                    if (entries[i].isIntersecting) {
                        loadMore();
                        return;
                    }
                }
            }, { rootMargin: '300px 0px', threshold: 0 });
            observer.observe(sentinel);
        }

        updateControls();
        if (!hasNext) {
            showEnd();
        } else {
            scheduleViewportFill();
        }
    });
})();
