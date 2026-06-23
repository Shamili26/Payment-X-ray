

import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import App from "./App";

// ─── shared fetch mock ───────────────────────────────────────────────────────
// App talks to the backend only through `fetch`. We stub it and route by URL +
// method so each test controls exactly what every endpoint returns. A config
// object provides defaults (logged-in user with one account/payee); individual
// tests override just the slice they exercise.
//
// We drive the UI with fireEvent rather than userEvent: the SPA lives in one
// large component tree that fully re-renders on every keystroke, and
// userEvent's per-character typing pushes each test past the timeout in jsdom.
// fireEvent applies a value in a single change event, which is both faster and
// deterministic here.

type MockResult = { ok?: boolean; status?: number; body?: unknown };

const last = <T,>(arr: T[]): T => arr[arr.length - 1];

const change = (el: Element, value: string) => fireEvent.change(el, { target: { value } });

const USER = {
    userId: 1,
    username: "jsmith42",
    email: "jane@example.com",
    firstName: "Jane",
    lastName: "Smith",
    phoneNumber: "+919876543210",
    role: "ROLE_USER",
};

const ACCOUNTS = [
    { accountId: 10, accountNumber: "1234567890123456", accountName: "Checking", accountBalance: 5000, accountStatus: "ACTIVE" },
];
const PAYEES = [
    { payeeId: 20, payeeNumber: "PAYE-001", payeeName: "Electric Co", amountDue: 100, dueDate: "2026-07-01" },
];
const FEE = { paymentAmount: 100, feeAmount: 5, totalAmount: 105 };
const CHALLENGE = { challengeId: "ch1", maskedMobile: "+91●●●●●●3210", expiresInSeconds: 300, message: "An OTP has been sent to +91●●●●●●3210" };
const CREATED_PAYMENT = {
    paymentId: 99, accountId: 10, accountName: "Checking", accountNumber: "1234567890123456",
    payeeId: 20, payeeName: "Electric Co", payeeNumber: "PAYE-001", paymentAmount: 100,
    feeAmount: 5, paymentDate: "2026-07-01", memo: null, status: "PENDING", updatedDatetime: "2026-06-23T10:00:00",
};

const NOT_AUTHED: MockResult = { ok: false, status: 401, body: { message: "Not authenticated" } };

type RouteConfig = {
    me?: MockResult;
    login?: MockResult;
    register?: MockResult;
    logout?: MockResult;
    profile?: MockResult;
    accounts?: MockResult;
    payees?: MockResult;
    fee?: MockResult;
    initiate?: MockResult;
    verify?: MockResult;
    del?: MockResult;
    payments?: MockResult;
};

function installFetch(cfg: RouteConfig = {}) {
    const c: Required<RouteConfig> = {
        me: cfg.me ?? { body: USER },
        login: cfg.login ?? { body: { user: USER } },
        register: cfg.register ?? { body: {} },
        logout: cfg.logout ?? { body: {} },
        profile: cfg.profile ?? { body: USER },
        accounts: cfg.accounts ?? { body: ACCOUNTS },
        payees: cfg.payees ?? { body: PAYEES },
        fee: cfg.fee ?? { body: FEE },
        initiate: cfg.initiate ?? { body: CHALLENGE },
        verify: cfg.verify ?? { body: CREATED_PAYMENT },
        del: cfg.del ?? { body: {} },
        payments: cfg.payments ?? { body: [] },
    };

    const fn = vi.fn(async (input: RequestInfo | URL, opts: RequestInit = {}) => {
        const url = String(input);
        const method = (opts.method ?? "GET").toUpperCase();
        let r: MockResult = {};
        if (url.endsWith("/auth/me")) r = c.me;
        else if (url.endsWith("/auth/login")) r = c.login;
        else if (url.endsWith("/auth/register")) r = c.register;
        else if (url.endsWith("/auth/logout")) r = c.logout;
        else if (url.endsWith("/auth/profile")) r = c.profile;
        else if (url.endsWith("/accounts")) r = c.accounts;
        else if (url.endsWith("/payees")) r = c.payees;
        else if (url.includes("/payment/fee")) r = c.fee;
        else if (url.endsWith("/payment/initiate")) r = c.initiate;
        else if (url.endsWith("/payment/verify")) r = c.verify;
        else if (method === "DELETE" && url.endsWith("/payment")) r = c.del;
        else if (url.endsWith("/payment")) r = c.payments;
        return {
            ok: r.ok ?? true,
            status: r.status ?? 200,
            json: async () => r.body ?? {},
        } as Response;
    });
    vi.stubGlobal("fetch", fn);
    return fn;
}

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

// ═══════════════════════════════════════════════════════════════════════════
// REGISTRATION
// ═══════════════════════════════════════════════════════════════════════════

describe("RegisterForm", () => {
    async function gotoRegister() {
        render(<App />);
        await screen.findByText("Secure payment infrastructure");
        fireEvent.click(screen.getAllByRole("button", { name: "Register" })[0]);
        await screen.findByPlaceholderText("jsmith42");
    }

    // Fills every text field except the account number boxes.
    function fillBaseFields() {
        change(screen.getByPlaceholderText("Jane"), "Jane");
        change(screen.getByPlaceholderText("Smith"), "Smith");
        change(screen.getByPlaceholderText("jsmith42"), "jsmith42");
        change(screen.getByPlaceholderText("jane@example.com"), "jane@example.com");
        change(screen.getByPlaceholderText("Create a strong password"), "Str0ng@Pass1");
        change(screen.getByPlaceholderText("9876543210"), "9876543210");
        change(document.querySelector('input[type="date"]')!, "1990-01-01");
    }

    function fillAccountBoxes(digits: [string, string, string, string]) {
        const boxes = screen.getAllByLabelText(/Account number group/);
        digits.forEach((d, i) => change(boxes[i], d));
    }

    const submit = () => fireEvent.click(last(screen.getAllByRole("button", { name: "Register" })));

    it("rejects an all-blank submit", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        submit();
        expect(await screen.findByText("All fields are required")).toBeInTheDocument();
    });

    it("shows the password strength meter as the password is typed", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        change(screen.getByPlaceholderText("Create a strong password"), "Str0ng@Pass1");
        // Each rule label renders (prefixed by a ✓/· marker) once the box is non-empty.
        expect(screen.getByText(/8\+ chars/)).toBeInTheDocument();
        expect(screen.getByText(/Symbol/)).toBeInTheDocument();
    });

    it("strips non-digits and caps the phone at 10 digits", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        const phone = screen.getByPlaceholderText("9876543210") as HTMLInputElement;
        change(phone, "98a76-543210999");
        expect(phone.value).toBe("9876543210");
    });

    it("rejects an invalid phone number", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        fillBaseFields();
        change(screen.getByPlaceholderText("9876543210"), "123");   // too few digits
        fillAccountBoxes(["1111", "2222", "3333", "4444"]);
        submit();
        expect(await screen.findByText("Enter a valid 10-digit Indian phone number.")).toBeInTheDocument();
    });

    it("rejects an account number that is not 16 digits", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        fillBaseFields();
        fillAccountBoxes(["1234", "", "", ""]);   // only 4 of 16 digits
        submit();
        expect(await screen.findByText(/Account number must be exactly 16 digits/)).toBeInTheDocument();
    });

    it("splits a pasted 16-digit number across the four account boxes", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        const boxes = screen.getAllByLabelText(/Account number group/) as HTMLInputElement[];
        fireEvent.paste(boxes[0], { clipboardData: { getData: () => "1234-5678-9012-3456" } });
        expect(boxes[0].value).toBe("1234");
        expect(boxes[1].value).toBe("5678");
        expect(boxes[2].value).toBe("9012");
        expect(boxes[3].value).toBe("3456");
    });

    it("auto-advances on a full box and steps back on backspace", async () => {
        installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        const boxes = screen.getAllByLabelText(/Account number group/) as HTMLInputElement[];
        change(boxes[0], "1234");
        expect(document.activeElement).toBe(boxes[1]);   // auto-advanced
        fireEvent.keyDown(boxes[1], { key: "Backspace" });   // empty box → step back
        expect(document.activeElement).toBe(boxes[0]);
    });

    it("registers successfully, then redirects to the sign-in form", async () => {
        const fetchFn = installFetch({ me: NOT_AUTHED });
        await gotoRegister();
        fillBaseFields();
        fillAccountBoxes(["1234", "5678", "9012", "3456"]);
        submit();
        await screen.findByText("Account created successfully.");

        // The register call carried the +91 phone and the joined 16-digit account.
        const call = fetchFn.mock.calls.find(([u]) => String(u).endsWith("/auth/register"));
        const sent = JSON.parse(String((call![1] as RequestInit).body));
        expect(sent.phoneNumber).toBe("+919876543210");
        expect(sent.accountNumber).toBe("1234567890123456");

        // After the 1200ms redirect timer the login form is shown again.
        await waitFor(() => expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument(), { timeout: 2500 });
    });

    it("surfaces a backend error when registration fails", async () => {
        installFetch({ me: NOT_AUTHED, register: { ok: false, status: 409, body: { message: "Username already taken" } } });
        await gotoRegister();
        fillBaseFields();
        fillAccountBoxes(["1234", "5678", "9012", "3456"]);
        submit();
        expect(await screen.findByText("Username already taken")).toBeInTheDocument();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// LOGIN — extra branches
// ═══════════════════════════════════════════════════════════════════════════

describe("LoginForm", () => {
    it("validates that both fields are required", async () => {
        installFetch({ me: NOT_AUTHED });
        render(<App />);
        await screen.findByText("Secure payment infrastructure");
        fireEvent.click(last(screen.getAllByRole("button", { name: "Sign in" })));
        expect(await screen.findByText("All fields required")).toBeInTheDocument();
    });

    it("toggles password visibility", async () => {
        installFetch({ me: NOT_AUTHED });
        render(<App />);
        await screen.findByText("Secure payment infrastructure");
        const pw = screen.getByPlaceholderText("Password") as HTMLInputElement;
        expect(pw.type).toBe("password");
        // The eye toggle is the only button inside the password field wrapper.
        fireEvent.click(pw.parentElement!.querySelector("button")!);
        expect(pw.type).toBe("text");
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// PAYMENT LIST
// ═══════════════════════════════════════════════════════════════════════════

describe("PaymentList", () => {
    const PAYMENTS = [
        { ...CREATED_PAYMENT, paymentId: 1, status: "PENDING", memo: "rent" },
        { ...CREATED_PAYMENT, paymentId: 2, status: "COMPLETED", payeeName: "Water Co" },
        { ...CREATED_PAYMENT, paymentId: 3, status: "CANCELLED", payeeName: "Gas Co" },
    ];

    it("renders payments with their statuses and a delete button only for non-completed ones", async () => {
        installFetch({ payments: { body: PAYMENTS } });
        render(<App />);
        expect(await screen.findByText("Payment History")).toBeInTheDocument();
        expect(await screen.findByText("Electric Co")).toBeInTheDocument();
        expect(screen.getByText("Water Co")).toBeInTheDocument();
        expect(screen.getByText("COMPLETED")).toBeInTheDocument();
        expect(screen.getByText("CANCELLED")).toBeInTheDocument();
        // PENDING + CANCELLED are deletable; COMPLETED is not → 2 delete buttons.
        expect(screen.getAllByRole("button", { name: "Delete" })).toHaveLength(2);
    });

    it("deletes a payment after confirmation", async () => {
        const fetchFn = installFetch({ payments: { body: [PAYMENTS[0]] } });
        vi.spyOn(window, "confirm").mockReturnValue(true);
        render(<App />);
        await screen.findByText("Electric Co");
        fireEvent.click(screen.getByRole("button", { name: "Delete" }));
        await waitFor(() => expect(screen.queryByText("Electric Co")).not.toBeInTheDocument());
        expect(fetchFn.mock.calls.some(([u, o]) => String(u).endsWith("/1/payment") && (o as RequestInit)?.method === "DELETE")).toBe(true);
    });

    it("does not delete when the confirm dialog is dismissed", async () => {
        const fetchFn = installFetch({ payments: { body: [PAYMENTS[0]] } });
        vi.spyOn(window, "confirm").mockReturnValue(false);
        render(<App />);
        await screen.findByText("Electric Co");
        fireEvent.click(screen.getByRole("button", { name: "Delete" }));
        expect(screen.getByText("Electric Co")).toBeInTheDocument();
        expect(fetchFn.mock.calls.some(([u]) => String(u).endsWith("/1/payment"))).toBe(false);
    });

    it("alerts when a delete fails", async () => {
        installFetch({ payments: { body: [PAYMENTS[0]] }, del: { ok: false, status: 500, body: { message: "Cannot delete" } } });
        vi.spyOn(window, "confirm").mockReturnValue(true);
        const alertSpy = vi.spyOn(window, "alert").mockImplementation(() => {});
        render(<App />);
        await screen.findByText("Electric Co");
        fireEvent.click(screen.getByRole("button", { name: "Delete" }));
        await waitFor(() => expect(alertSpy).toHaveBeenCalledWith("Cannot delete"));
    });

    it("shows a friendly empty state when there are no payments", async () => {
        installFetch({ payments: { body: [] } });
        render(<App />);
        expect(await screen.findByText(/No payments yet/)).toBeInTheDocument();
    });

    it("shows an error when the payment list fails to load", async () => {
        installFetch({ payments: { ok: false, status: 500, body: { message: "List unavailable" } } });
        render(<App />);
        expect(await screen.findByText("List unavailable")).toBeInTheDocument();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// PAYMENT FLOW — Details → Review → OTP → Confirmation
// ═══════════════════════════════════════════════════════════════════════════

describe("PaymentFlow", () => {
    async function gotoDetails(cfg: RouteConfig = {}) {
        const fetchFn = installFetch(cfg);
        render(<App />);
        await screen.findByText("Payment History");
        fireEvent.click(screen.getByRole("button", { name: "+ New Payment" }));
        await screen.findByText("Payment Details");
        return { fetchFn };
    }

    // Fills the details form with valid data and waits for the fee preview.
    async function fillValidDetails() {
        const [accountSel, payeeSel] = screen.getAllByRole("combobox");
        change(accountSel, "10");
        change(payeeSel, "20");
        change(screen.getByPlaceholderText("0.00"), "100");
        // Fee preview is debounced 500ms then rendered (Payment / Fee / Total).
        expect(await screen.findByText("Total", undefined, { timeout: 3000 })).toBeInTheDocument();
    }

    const reviewBtn = () => screen.getByRole("button", { name: /Review Payment/ });

    it("validates required fields on the details step", async () => {
        await gotoDetails();
        fireEvent.click(reviewBtn());
        expect(await screen.findByText("Select a from account")).toBeInTheDocument();
        expect(screen.getByText("Select a payee")).toBeInTheDocument();
        expect(screen.getByText("Enter a valid amount")).toBeInTheDocument();
    });

    it("advances to Review with the entered details, then edits back", async () => {
        await gotoDetails();
        await fillValidDetails();
        fireEvent.click(reviewBtn());
        expect(await screen.findByText("Review Payment")).toBeInTheDocument();
        expect(screen.getByText("Total Debit")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /Edit/ }));
        expect(await screen.findByText("Payment Details")).toBeInTheDocument();
    });

    it("runs the full happy path to the confirmation screen", async () => {
        await gotoDetails();
        await fillValidDetails();
        fireEvent.click(reviewBtn());
        await screen.findByText("Review Payment");
        fireEvent.click(screen.getByRole("button", { name: /Confirm & Submit/ }));

        // OTP step
        expect(await screen.findByText("Verify OTP")).toBeInTheDocument();
        change(screen.getByLabelText("One-time password"), "123456");
        fireEvent.click(screen.getByRole("button", { name: /Verify/ }));

        // Confirmation step
        expect(await screen.findByText("Payment Submitted!")).toBeInTheDocument();
        expect(screen.getByText("#99")).toBeInTheDocument();
        // "Make Another Payment" returns to a fresh details step.
        fireEvent.click(screen.getByRole("button", { name: /Make Another Payment/ }));
        expect(await screen.findByText("Payment Details")).toBeInTheDocument();
    });

    it("rejects a badly formatted OTP and supports resend + back", async () => {
        const { fetchFn } = await gotoDetails();
        await fillValidDetails();
        fireEvent.click(reviewBtn());
        await screen.findByText("Review Payment");
        fireEvent.click(screen.getByRole("button", { name: /Confirm & Submit/ }));
        await screen.findByText("Verify OTP");

        // Too-short code is rejected client-side.
        change(screen.getByLabelText("One-time password"), "12");
        fireEvent.click(screen.getByRole("button", { name: /Verify/ }));
        expect(await screen.findByText(/Enter the 6-digit OTP/)).toBeInTheDocument();

        // Resend requests a fresh challenge (second /payment/initiate call).
        fireEvent.click(screen.getByRole("button", { name: "Resend OTP" }));
        await waitFor(() =>
            expect(fetchFn.mock.calls.filter(([u]) => String(u).endsWith("/payment/initiate")).length).toBeGreaterThanOrEqual(2));

        // Back returns to the Review screen (exact name — "← Back to payments"
        // from the flow header also contains "Back").
        fireEvent.click(screen.getByRole("button", { name: "← Back" }));
        expect(await screen.findByText("Review Payment")).toBeInTheDocument();
    });

    it("disables verification once the OTP countdown reaches zero", async () => {
        // expiresInSeconds must be truthy (the component falls back to 300 on a
        // falsy value), so start at 1s and let the countdown tick to expiry.
        await gotoDetails({ initiate: { body: { ...CHALLENGE, expiresInSeconds: 1 } } });
        await fillValidDetails();
        fireEvent.click(reviewBtn());
        await screen.findByText("Review Payment");
        fireEvent.click(screen.getByRole("button", { name: /Confirm & Submit/ }));
        await screen.findByText("Verify OTP");
        expect(await screen.findByText("Code expired", undefined, { timeout: 4000 })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Verify/ })).toBeDisabled();
    });

    it("alerts when initiating the OTP challenge fails", async () => {
        const alertSpy = vi.spyOn(window, "alert").mockImplementation(() => {});
        await gotoDetails({ initiate: { ok: false, status: 400, body: { message: "Insufficient balance" } } });
        await fillValidDetails();
        fireEvent.click(reviewBtn());
        await screen.findByText("Review Payment");
        fireEvent.click(screen.getByRole("button", { name: /Confirm & Submit/ }));
        await waitFor(() => expect(alertSpy).toHaveBeenCalledWith("Insufficient balance"));
    });

    it("blocks the review step when the fee cannot be calculated", async () => {
        await gotoDetails({ fee: { ok: false, status: 400, body: { message: "no tier" } } });
        const [accountSel, payeeSel] = screen.getAllByRole("combobox");
        change(accountSel, "10");
        change(payeeSel, "20");
        change(screen.getByPlaceholderText("0.00"), "100");
        fireEvent.click(reviewBtn());
        expect(await screen.findByText(/Could not calculate fee/)).toBeInTheDocument();
    });

    it("cancels back to the payment list", async () => {
        await gotoDetails();
        fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
        expect(await screen.findByText("Payment History")).toBeInTheDocument();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// PROFILE
// ═══════════════════════════════════════════════════════════════════════════

describe("ProfilePage", () => {
    async function gotoProfile(cfg: RouteConfig = {}) {
        const fetchFn = installFetch(cfg);
        render(<App />);
        await screen.findByText("Payment History");
        fireEvent.click(screen.getByRole("button", { name: "Profile" }));
        await screen.findByText("Your Profile");
        return { fetchFn };
    }

    it("prefills the editable fields and strips the +91 prefix from the phone", async () => {
        await gotoProfile();
        expect((screen.getByPlaceholderText("Jane") as HTMLInputElement).value).toBe("Jane");
        expect((screen.getByPlaceholderText("9876543210") as HTMLInputElement).value).toBe("9876543210");
    });

    it("validates required fields, email format and phone format", async () => {
        await gotoProfile();
        change(screen.getByPlaceholderText("Jane"), "");
        fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
        expect(await screen.findByText(/First name, last name and email are required/)).toBeInTheDocument();

        change(screen.getByPlaceholderText("Jane"), "Jane");
        change(screen.getByPlaceholderText("jane@example.com"), "not-an-email");
        fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
        expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();

        change(screen.getByPlaceholderText("jane@example.com"), "jane@example.com");
        change(screen.getByPlaceholderText("9876543210"), "123");
        fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
        expect(await screen.findByText("Enter a valid 10-digit Indian phone number.")).toBeInTheDocument();
    });

    it("saves successfully and shows a confirmation", async () => {
        const { fetchFn } = await gotoProfile();
        fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
        expect(await screen.findByText("Profile updated successfully.")).toBeInTheDocument();
        const call = fetchFn.mock.calls.find(([u]) => String(u).endsWith("/auth/profile"));
        expect((call![1] as RequestInit).method).toBe("PUT");
    });

    it("surfaces a backend error on save failure", async () => {
        await gotoProfile({ profile: { ok: false, status: 400, body: { message: "Email in use" } } });
        fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
        expect(await screen.findByText("Email in use")).toBeInTheDocument();
    });

    it("returns to the list via the back link", async () => {
        await gotoProfile();
        fireEvent.click(screen.getByRole("button", { name: /Back to payments/ }));
        expect(await screen.findByText("Payment History")).toBeInTheDocument();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// APP ROOT — bootstrap + logout
// ═══════════════════════════════════════════════════════════════════════════

describe("App root", () => {
    it("restores an existing session straight to the payment list", async () => {
        installFetch({ payments: { body: [] } });
        render(<App />);
        expect(await screen.findByText("Payment History")).toBeInTheDocument();
    });

    it("logs the user out and returns to the sign-in screen", async () => {
        const fetchFn = installFetch({ payments: { body: [] } });
        render(<App />);
        await screen.findByText("Payment History");
        fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
        expect(await screen.findByText("Secure payment infrastructure")).toBeInTheDocument();
        expect(fetchFn.mock.calls.some(([u, o]) => String(u).endsWith("/auth/logout") && (o as RequestInit)?.method === "POST")).toBe(true);
    });

    it("still clears the session locally even if the logout request fails", async () => {
        installFetch({ payments: { body: [] }, logout: { ok: false, status: 500, body: {} } });
        render(<App />);
        await screen.findByText("Payment History");
        fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
        expect(await screen.findByText("Secure payment infrastructure")).toBeInTheDocument();
    });
});