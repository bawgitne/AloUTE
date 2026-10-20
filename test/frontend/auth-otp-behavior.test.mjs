/**
 * AloUTE — Auth & Registration OTP Frontend Behavior Regression Tests
 *
 * Built-in Node test suite (node:test + assert/strict + vm).
 * Evaluates production auth.js against a lightweight DOM/window/fetch harness.
 */

import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '../..');

const authSource = fs.readFileSync(
    path.resolve(repoRoot, 'src/main/resources/static/js/auth.js'),
    'utf-8'
);

function createMockHarness(initialUrl = 'http://localhost:8080/login') {
    const elementsById = {};

    function makeElement(tag, id = '', className = '') {
        const listeners = {};
        const el = {
            tagName: tag.toUpperCase(),
            _id: id,
            get id() { return this._id || ''; },
            set id(val) { this._id = val; elementsById[val] = this; },
            className: className,
            classList: {
                _classes: new Set(className.split(' ').filter(Boolean)),
                add(c) { this._classes.add(c); el.className = Array.from(this._classes).join(' '); },
                remove(c) { this._classes.delete(c); el.className = Array.from(this._classes).join(' '); },
                contains(c) { return this._classes.has(c); }
            },
            attributes: {},
            setAttribute(k, v) { this.attributes[k] = String(v); },
            getAttribute(k) { return this.attributes[k] || null; },
            removeAttribute(k) { delete this.attributes[k]; },
            value: '',
            textContent: '',
            innerHTML: '',
            children: [],
            prepend(child) { this.children.unshift(child); },
            appendChild(child) { this.children.push(child); },
            querySelector(sel) { return null; },
            querySelectorAll(sel) { return []; },
            scrollIntoView() {},
            focus() {},
            disabled: false,
            dataset: {},
            addEventListener(event, fn) {
                if (!listeners[event]) listeners[event] = [];
                listeners[event].push(fn);
            },
            dispatchEvent(event) {
                const list = listeners[event.type] || [];
                for (const fn of list) fn(event);
            }
        };
        if (id) elementsById[id] = el;
        return el;
    }

    const forms = {
        login: makeElement('form', 'login-form'),
        register: makeElement('form', 'register-form'),
        verifyOtp: makeElement('form', 'verify-otp-form')
    };
    forms.login.setAttribute('data-action', 'login');
    forms.register.setAttribute('data-action', 'register');
    forms.verifyOtp.setAttribute('data-action', 'verify-otp');

    const document = {
        getElementById(id) {
            return elementsById[id] || null;
        },
        createElement(tag) {
            return makeElement(tag);
        },
        querySelector(sel) {
            if (sel.includes('login')) return forms.login;
            if (sel.includes('register')) return forms.register;
            if (sel.includes('verify-otp')) return forms.verifyOtp;
            return null;
        },
        querySelectorAll(sel) {
            return [];
        },
        cookie: 'XSRF-TOKEN=mock-token',
        body: makeElement('body')
    };

    const window = {
        location: new URL(initialUrl),
        document: document,
        setTimeout(fn, ms) { fn(); return 1; },
        setInterval(fn, ms) { return 2; },
        clearInterval(id) {},
        addEventListener(event, fn) {},
        URLSearchParams: URLSearchParams
    };

    return { window, document, elementsById, forms, makeElement };
}

describe('Auth & Registration OTP Frontend Behavior', () => {

    it('displays unverified login recovery link pointing to /verify-otp?email=... on 401 AUTH_ACCOUNT_NOT_VERIFIED', async () => {
        const harness = createMockHarness('http://localhost:8080/login');
        const emailInput = harness.makeElement('input', 'email');
        emailInput.value = 'pending-user@aloute.local';

        const passwordInput = harness.makeElement('input', 'password');
        passwordInput.value = 'Pass@123';

        const submitBtn = harness.makeElement('button', 'login-submit-btn');

        const mockBody = {
            success: false,
            error: {
                code: 'AUTH_ACCOUNT_NOT_VERIFIED',
                message: 'Tài khoản chưa được kích hoạt.'
            }
        };

        harness.window.fetch = async (url, options) => {
            return {
                status: 401,
                ok: false,
                headers: { get: () => 'application/json' },
                text: async () => JSON.stringify(mockBody),
                json: async () => mockBody
            };
        };

        const context = vm.createContext({
            window: harness.window,
            document: harness.document,
            fetch: harness.window.fetch,
            URLSearchParams: URLSearchParams,
            encodeURIComponent: encodeURIComponent,
            decodeURIComponent: decodeURIComponent
        });

        vm.runInContext(authSource, context);

        // Submit login form
        harness.forms.login.dispatchEvent({ type: 'submit', preventDefault() {} });

        // Wait a tick for async fetch
        await new Promise(r => setTimeout(r, 30));

        const statusBox = harness.document.getElementById('auth-status');
        assert.ok(statusBox, 'Status box should be created');
        assert.ok(statusBox.innerHTML.includes('/verify-otp?email=pending-user%40aloute.local'), 'Recovery link should include safely encoded email');
        assert.ok(statusBox.innerHTML.includes('Bấm vào đây để xác thực OTP'), 'Link text should guide user to verify');
        assert.ok(!statusBox.innerHTML.includes('Email hoặc mật khẩu không chính xác'), 'Must not display wrong password error for unverified account');
    });

    it('displays generic error and NO account existence clues on 401 AUTH_BAD_CREDENTIALS', async () => {
        const harness = createMockHarness('http://localhost:8080/login');
        const emailInput = harness.makeElement('input', 'email');
        emailInput.value = 'wrong@aloute.local';

        const passwordInput = harness.makeElement('input', 'password');
        passwordInput.value = 'WrongPass';

        const submitBtn = harness.makeElement('button', 'login-submit-btn');

        const mockBody = {
            success: false,
            error: {
                code: 'AUTH_BAD_CREDENTIALS',
                message: 'Bad credentials'
            }
        };

        harness.window.fetch = async () => ({
            status: 401,
            ok: false,
            headers: { get: () => 'application/json' },
            text: async () => JSON.stringify(mockBody),
            json: async () => mockBody
        });

        const context = vm.createContext({
            window: harness.window,
            document: harness.document,
            fetch: harness.window.fetch,
            URLSearchParams: URLSearchParams,
            encodeURIComponent: encodeURIComponent,
            decodeURIComponent: decodeURIComponent
        });

        vm.runInContext(authSource, context);
        harness.forms.login.dispatchEvent({ type: 'submit', preventDefault() {} });
        await new Promise(r => setTimeout(r, 30));

        const statusBox = harness.document.getElementById('auth-status');
        assert.ok(statusBox, 'Status box should be created');
        assert.ok(statusBox.textContent.includes('Email hoặc mật khẩu không chính xác'));
        assert.ok(!statusBox.innerHTML.includes('/verify-otp'), 'Generic 401 must NOT render verify link');
    });

    it('uses delivery-neutral copy without misleading claims on resend and registration', async () => {
        const harness = createMockHarness('http://localhost:8080/verify-otp?email=qa%40aloute.local');
        const emailInput = harness.makeElement('input', 'otp-email');
        emailInput.value = 'qa@aloute.local';

        const resendBtn = harness.makeElement('button', 'resend-otp-btn');

        const mockBody = {
            success: true,
            message: 'OTP request processed'
        };

        harness.window.fetch = async () => ({
            status: 200,
            ok: true,
            headers: { get: () => 'application/json' },
            text: async () => JSON.stringify(mockBody),
            json: async () => mockBody
        });

        const context = vm.createContext({
            window: harness.window,
            document: harness.document,
            fetch: harness.window.fetch,
            URLSearchParams: URLSearchParams,
            encodeURIComponent: encodeURIComponent,
            decodeURIComponent: decodeURIComponent
        });

        vm.runInContext(authSource, context);
        resendBtn.dispatchEvent({ type: 'click', preventDefault() {} });
        await new Promise(r => setTimeout(r, 30));

        const statusBox = harness.document.getElementById('auth-status');
        assert.ok(statusBox);
        assert.ok(!statusBox.textContent.includes('Đã gửi'), 'Must not claim email has been sent');
        assert.ok(statusBox.textContent.includes('Yêu cầu gửi lại mã xác thực đã được xử lý'));
    });

    it('falls back to manual email input when accessing /verify-otp without URL parameter', async () => {
        const harness = createMockHarness('http://localhost:8080/verify-otp');
        const emailHidden = harness.makeElement('input', 'otp-email');
        const emailManual = harness.makeElement('input', 'otp-email-input');
        const manualGroup = harness.makeElement('div', 'email-manual-group', 'd-none');
        const displayGroup = harness.makeElement('div', 'email-display-group');

        const context = vm.createContext({
            window: harness.window,
            document: harness.document,
            fetch: async () => ({ ok: true, text: async () => '{}' }),
            URLSearchParams: URLSearchParams,
            encodeURIComponent: encodeURIComponent,
            decodeURIComponent: decodeURIComponent
        });

        vm.runInContext(authSource, context);

        assert.ok(!manualGroup.classList.contains('d-none'), 'Manual email group should be visible when email param is absent');
        assert.ok(displayGroup.classList.contains('d-none'), 'Display group should be hidden when email param is absent');
    });
});
