/**
 * AloUTE — Core HTTP Client
 *
 * Canonical shared HTTP transport client complying with frontend_rule.md section 14:
 * - Credentials policy: credentials = 'include'
 * - CSRF protection: extracts XSRF-TOKEN cookie and attaches X-XSRF-TOKEN header on mutations
 * - Common headers: Accept: 'application/json'
 * - Safe JSON reader handling empty and malformed payloads
 */
(function () {
    'use strict';

    if (typeof window !== 'undefined' && window.__alouteHttpInitialized) return;
    if (typeof window !== 'undefined') window.__alouteHttpInitialized = true;

    function getCsrfToken() {
        if (typeof document === 'undefined' || !document.cookie) return null;
        const cookie = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
        return cookie ? decodeURIComponent(cookie.split('=').slice(1).join('=')) : null;
    }

    async function readJson(response) {
        if (!response) return null;
        try {
            const text = await response.text();
            if (!text || !text.trim()) return null;
            return JSON.parse(text);
        } catch (_) {
            return null;
        }
    }

    async function request(url, options) {
        const opts = Object.assign({}, options || {});
        const method = (opts.method || 'GET').toUpperCase();
        const headers = Object.assign({}, opts.headers || {});

        if (!headers['Accept']) {
            headers['Accept'] = 'application/json';
        }

        const isMutation = method === 'POST' || method === 'PUT' || method === 'DELETE' || method === 'PATCH';
        if (isMutation && !headers['X-XSRF-TOKEN']) {
            const token = getCsrfToken();
            if (token) {
                headers['X-XSRF-TOKEN'] = token;
            }
        }

        let body = opts.body;
        if (body && typeof body === 'object' && !(typeof FormData !== 'undefined' && body instanceof FormData)) {
            if (!headers['Content-Type']) {
                headers['Content-Type'] = 'application/json';
            }
            body = JSON.stringify(body);
        }

        return fetch(url, {
            method: method,
            headers: headers,
            body: body,
            credentials: opts.credentials || 'include',
            signal: opts.signal
        });
    }

    if (typeof window !== 'undefined') {
        window.alouteHttp = {
            request: request,
            readJson: readJson
        };
    }
})();
