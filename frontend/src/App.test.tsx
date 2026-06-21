import { describe, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";

// ─── fetch mock ────────────────────────────────────────────────────────────
// App talks to the backend exclusively through `fetch`. We stub it and route by
// URL so each test controls exactly what the API returns.

type MockResult = { ok?: boolean; status?: number; body?: unknown };
type Handler = (url: string, opts: RequestInit) => MockResult;

// Array.prototype.at is ES2022; the app tsconfig targets ES2020, so use a helper.
const last = <T,>(arr: T[]): T => arr[arr.length - 1];

function mockFetch(handler: Handler) {
    const fn = vi.fn(async (input: RequestInfo | URL, opts: RequestInit = {}) => {
        const result = handler(String(input), opts);
        return {
            ok: result.ok ?? true,
            status: result.status ?? 200,
            json: async () => result.body ?? {},
        } as Response;
    });
    vi.stubGlobal("fetch", fn);
    return fn;
}

const NOT_AUTHED: MockResult = { ok: false, status: 401, body: { message: "Not authenticated" } };

const USER = {
    userId: 1,
    username: "jsmith42",
    email: "jane@example.com",
    firstName: "Jane",
    lastName: "Smith",
    phoneNumber: "+919876543210",
    role: "ROLE_USER",
};

describe("App — session bootstrap & auth routing", () => {
    it("shows the sign-in screen when no valid session cookie exists", async () => {
        mockFetch((url) => (url.endsWith("/auth/me") ? NOT_AUTHED : {}));

        render(<App />);

        // The auth landing page renders once the /auth/me probe resolves 401. The
        // login form (its username field) is the unambiguous signal it's mounted —
        // "Sign in" appears both as the mode-switch tab and the submit button.
        expect(await screen.findByText("Secure payment infrastructure")).toBeInTheDocument();
        expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument();
    });

    it("switches to the registration form and validates empty submit", async () => {
        mockFetch((url) => (url.endsWith("/auth/me") ? NOT_AUTHED : {}));
        const user = userEvent.setup();

        render(<App />);
        await screen.findByText("Secure payment infrastructure");

        // Click the "Register" tab (the first "Register" control) to reveal the form.
        await user.click(screen.getAllByRole("button", { name: "Register" })[0]);
        expect(await screen.findByPlaceholderText("jsmith42")).toBeInTheDocument();   // Username field

        // Submitting with everything blank surfaces the required-fields error. The
        // submit button is the last "Register" control on the page.
        await user.click(last(screen.getAllByRole("button", { name: "Register" })));
        expect(await screen.findByText("All fields are required")).toBeInTheDocument();
    });

    it("logs in and lands on the payment history screen", async () => {
        const fetchFn = mockFetch((url) => {
            if (url.endsWith("/auth/me")) return NOT_AUTHED;             // initial probe: logged out
            if (url.endsWith("/auth/login")) return { body: { user: USER } };
            if (url.endsWith("/payment")) return { body: [] };           // empty history
            return {};
        });
        const user = userEvent.setup();

        render(<App />);
        await screen.findByText("Secure payment infrastructure");

        await user.type(screen.getByPlaceholderText("you@example.com"), "jsmith42");
        await user.type(screen.getByPlaceholderText("Password"), "Str0ng@Pass1");
        await user.click(last(screen.getAllByRole("button", { name: "Sign in" })));

        // On success the app routes to the payment list, which then loads payments.
        expect(await screen.findByText("Payment History")).toBeInTheDocument();
        expect(await screen.findByText(/No payments yet/)).toBeInTheDocument();

        // The login request was a POST to the right endpoint.
        const loginCall = fetchFn.mock.calls.find(([u]) => String(u).endsWith("/auth/login"));
        expect(loginCall).toBeTruthy();
        expect(loginCall![1]).toMatchObject({ method: "POST" });
    });

    it("shows a backend error message when login fails", async () => {
        mockFetch((url) => {
            if (url.endsWith("/auth/me")) return NOT_AUTHED;
            if (url.endsWith("/auth/login"))
                return { ok: false, status: 401, body: { message: "Invalid username or password" } };
            return {};
        });
        const user = userEvent.setup();

        render(<App />);
        await screen.findByText("Secure payment infrastructure");

        await user.type(screen.getByPlaceholderText("you@example.com"), "jsmith42");
        await user.type(screen.getByPlaceholderText("Password"), "wrongpass");
        await user.click(last(screen.getAllByRole("button", { name: "Sign in" })));

        expect(await screen.findByText("Invalid username or password")).toBeInTheDocument();
        // Still on the auth screen — no navigation occurred.
        await waitFor(() => expect(screen.queryByText("Payment History")).not.toBeInTheDocument());
    });
});