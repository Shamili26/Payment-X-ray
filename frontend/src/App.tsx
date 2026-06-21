import {
    useState,
    useEffect,
    useCallback,
    type CSSProperties,
    type ReactNode,
    type ChangeEvent,
    type FormEvent,
    type KeyboardEvent,
    type ClipboardEvent,
} from "react";
import type {
    AuthResponse,
    UserInfo,
    Account,
    Payee,
    FeePreview,
    Payment,
    OtpChallenge,
    PaymentFormData,
    PaymentMeta,
    ProfileUpdate,
} from "./types";

const API = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8084/api";

// ─── helpers ─────────────────────────────────────────────────────────────────

function fmt(n: number): string {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(n);
}
function fmtDate(d: string): string {
    if (!d) return "";
    const [y, m, day] = d.split("-");
    return `${day}/${m}/${y}`;
}

function todayISO(): string {
    return new Date().toISOString().split("T")[0];
}
// Mask an account number as ****-****-****-1234 (first 12 digits masked, last 4 shown)
function maskAccount(num: string | number | null | undefined): string {
    if (!num) return "";
    const last4 = String(num).slice(-4);
    return `****-****-****-${last4}`;
}

async function api<T = unknown>(path: string, opts: RequestInit = {}): Promise<T> {
    // The JWT lives in an httpOnly cookie (set by the backend at login) and is
    // not accessible to JavaScript. `credentials: "include"` makes the browser
    // send that cookie with every request — no token is read or stored here.
    const res = await fetch(`${API}${path}`, {
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        ...opts,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((data as { message?: string }).message || "Request failed");
    return data as T;
}

function errMessage(e: unknown): string {
    return e instanceof Error ? e.message : "Something went wrong";
}

// Auto-logout after this much inactivity. MUST match the backend
// app.session.idle-timeout-minutes (15) — the server enforces the same window
// and will reject requests past it; this hook keeps the UI in sync so the user
// is returned to the login screen instead of silently hitting a 401.
const IDLE_TIMEOUT_MS = 15 * 60 * 1000;

// Calls `onIdle` once the user has been inactive for IDLE_TIMEOUT_MS while
// `enabled`. Any of the listed user-interaction events resets the countdown
// (throttled so a flood of mousemove events doesn't reschedule constantly).
function useIdleTimeout(enabled: boolean, onIdle: () => void) {
    useEffect(() => {
        if (!enabled) return;

        let timer: ReturnType<typeof setTimeout>;
        let lastReset = 0;

        const reset = () => {
            const now = Date.now();
            if (now - lastReset < 1000) return; // throttle reschedules to ~1/sec
            lastReset = now;
            clearTimeout(timer);
            timer = setTimeout(onIdle, IDLE_TIMEOUT_MS);
        };

        const events = ["mousemove", "mousedown", "keydown", "scroll", "touchstart", "click"];
        events.forEach(e => window.addEventListener(e, reset, { passive: true }));
        timer = setTimeout(onIdle, IDLE_TIMEOUT_MS);

        return () => {
            clearTimeout(timer);
            events.forEach(e => window.removeEventListener(e, reset));
        };
    }, [enabled, onIdle]);
}

// ─── pastel theme palette ────────────────────────────────────────────────────
const C = {
    accent: "#a78bfa",        // soft lavender (brand accent)
    accentDeep: "#7c3aed",    // deeper violet for links / emphasis
    text: "#3f3d56",          // primary readable text on light bg
    muted: "#9b97b3",         // secondary text
    faint: "#b7b2cc",         // tertiary / counters
    line: "#ece6f7",          // hairline borders
    inputBg: "#faf9ff",
    inputBorder: "#e5e0f5",
    done: "#10b981",          // pastel green
    pending: "#f59e0b",       // pastel amber
    cancelled: "#fb7185",     // pastel rose
};

// ─── simple UI atoms ─────────────────────────────────────────────────────────

const EyeIcon = ({ open }: { open: boolean }) => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        {open ? (<><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></>) :
            (<><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></>)}
    </svg>
);
const Spin = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
         style={{ animation: "spin .8s linear infinite" }}>
        <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" opacity=".2"/><path d="M21 12a9 9 0 00-9-9"/>
    </svg>
);

const S: Record<string, CSSProperties> = {
    page: { minHeight: "100vh", width: "100%", background: "linear-gradient(135deg, #fce7f3 0%, #ede9fe 45%, #dbeafe 100%)", fontFamily: "'Inter',-apple-system,sans-serif", color: C.text },
    card: { background: "#ffffff", border: `1px solid ${C.line}`, borderRadius: "16px", padding: "32px", boxShadow: "0 8px 30px rgba(167,139,250,0.12)" },
    label: { fontSize: "11px", fontWeight: 600, color: C.muted, letterSpacing: "0.07em", textTransform: "uppercase", display: "block", marginBottom: "6px" },
    input: { width: "100%", padding: "10px 13px", background: C.inputBg, border: `1px solid ${C.inputBorder}`, borderRadius: "10px", color: C.text, fontSize: "14px", outline: "none", boxSizing: "border-box" },
    select: { width: "100%", padding: "10px 13px", background: "#ffffff", border: `1px solid ${C.inputBorder}`, borderRadius: "10px", color: C.text, fontSize: "14px", outline: "none" },
    btn: { padding: "11px 28px", borderRadius: "10px", border: "none", fontWeight: 600, fontSize: "14px", cursor: "pointer", transition: "opacity .2s, transform .1s" },
    btnPrimary: { background: "linear-gradient(135deg,#c4b5fd,#a5b4fc)", color: "#4c1d95", boxShadow: "0 4px 14px rgba(165,180,252,0.45)" },
    btnGhost: { background: "#f5f3ff", border: "1px solid #e9d5ff", color: C.accentDeep },
    error: { padding: "10px 14px", background: "#fee2e2", border: "1px solid #fecaca", borderRadius: "10px", color: "#dc2626", fontSize: "13px", marginBottom: "14px" },
    success: { padding: "10px 14px", background: "#d1fae5", border: "1px solid #a7f3d0", borderRadius: "10px", color: "#059669", fontSize: "13px" },
    row: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px" },
    teal: { color: C.accent },
    muted: { color: C.muted, fontSize: "13px" },
};

interface FieldProps {
    label: string;
    error?: string;
    children: ReactNode;
}
function Field({ label, error, children }: FieldProps) {
    return (
        <div style={{ display: "flex", flexDirection: "column", gap: "5px" }}>
            <label style={S.label}>{label}</label>
            {children}
            {error && <span style={{ fontSize: "11px", color: "#dc2626" }}>{error}</span>}
        </div>
    );
}

interface BtnProps {
    onClick?: () => void;
    loading?: boolean;
    children: ReactNode;
    ghost?: boolean;
    disabled?: boolean;
    style?: CSSProperties;
}
function Btn({ onClick, loading, children, ghost, disabled, style = {} }: BtnProps) {
    return (
        <button onClick={onClick} disabled={loading || disabled}
                style={{ ...S.btn, ...(ghost ? S.btnGhost : S.btnPrimary), opacity: (loading || disabled) ? 0.55 : 1, display: "flex", alignItems: "center", gap: "7px", ...style }}>
            {loading && <Spin />}{children}
        </button>
    );
}

// ─── PW strength ──────────────────────────────────────────────────────────────
type PwCheck = [string, (v: string) => boolean];
const pwChecks: PwCheck[] = [
    ["8+ chars", v => v.length >= 8],
    ["Uppercase", v => /[A-Z]/.test(v)],
    ["Lowercase", v => /[a-z]/.test(v)],
    ["Number", v => /\d/.test(v)],
    ["Symbol", v => /[@$!%*?&]/.test(v)],
];
function PwStrength({ pw }: { pw: string }) {
    if (!pw) return null;
    const score = pwChecks.filter(([, f]) => f(pw)).length;
    const col = score < 2 ? C.cancelled : score < 4 ? C.pending : C.done;
    return (
        <div style={{ marginTop: 6 }}>
            <div style={{ display: "flex", gap: 3, marginBottom: 6 }}>
                {[1,2,3,4,5].map(i => <div key={i} style={{ flex:1, height:3, borderRadius:2, background: i<=score ? col : C.line, transition:"background .3s" }} />)}
            </div>
            <div style={{ display:"flex", flexWrap:"wrap", gap:5 }}>
                {pwChecks.map(([lbl, f]) => (
                    <span key={lbl} style={{ fontSize:11, color: f(pw)?C.accentDeep:C.faint, display:"flex", alignItems:"center", gap:3 }}>
            {f(pw) ? "✓" : "·"} {lbl}
          </span>
                ))}
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// AUTH SECTION
// ═══════════════════════════════════════════════════════════════════

interface LoginFormProps {
    onSuccess: (user: UserInfo) => void;
    onSwitch: () => void;
}
function LoginForm({ onSuccess, onSwitch }: LoginFormProps) {
    const [f, setF] = useState({ usernameOrEmail: "", password: "" });
    const [showPw, setShowPw] = useState(false);
    const [err, setErr] = useState("");
    const [loading, setLoading] = useState(false);
    const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement>) => setF(p => ({ ...p, [k]: e.target.value }));

    const submit = async (ev: FormEvent) => {
        ev.preventDefault(); setErr("");
        if (!f.usernameOrEmail || !f.password) { setErr("All fields required"); return; }
        setLoading(true);
        try {
            // On success the backend sets an httpOnly JWT cookie; nothing to store here.
            const data = await api<AuthResponse>("/auth/login", { method: "POST", body: JSON.stringify(f) });
            onSuccess(data.user);
        } catch (e) { setErr(errMessage(e)); } finally { setLoading(false); }
    };

    return (
        <form onSubmit={submit} style={{ display:"flex", flexDirection:"column", gap:18 }}>
            {err && <div style={S.error}>{err}</div>}
            <Field label="Username or Email">
                <input style={S.input} value={f.usernameOrEmail} onChange={set("usernameOrEmail")} placeholder="you@example.com" autoComplete="username" />
            </Field>
            <Field label="Password">
                <div style={{ position:"relative" }}>
                    <input style={{ ...S.input, paddingRight:40 }} type={showPw?"text":"password"} value={f.password} onChange={set("password")} placeholder="Password" autoComplete="current-password" />
                    <button type="button" onClick={() => setShowPw(p=>!p)} style={{ position:"absolute", right:12, top:"50%", transform:"translateY(-50%)", background:"none", border:"none", color:C.muted, cursor:"pointer", padding:0, display:"flex" }}>
                        <EyeIcon open={showPw} />
                    </button>
                </div>
            </Field>
            <Btn loading={loading}>Sign in</Btn>
            <p style={{ textAlign:"center", ...S.muted, margin:0 }}>New? <button type="button" onClick={onSwitch} style={{ background:"none", border:"none", color:C.accentDeep, fontWeight:600, cursor:"pointer", fontSize:13 }}>Register</button></p>
        </form>
    );
}

function RegisterForm({ onSwitch }: { onSwitch: () => void }) {
    const [f, setF] = useState({ firstName:"", lastName:"", username:"", email:"", password:"", phoneNumber:"", dateOfBirth:"" });
    const [acct, setAcct] = useState<string[]>(["", "", "", ""]);   // four 4-digit groups → 16-digit account number
    const [showPw, setShowPw] = useState(false);
    const [err, setErr] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);
    const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement>) => setF(p => ({ ...p, [k]: e.target.value }));
    // Allow only up to 10 numeric digits for the Indian phone number
    const setPhone = (e: ChangeEvent<HTMLInputElement>) => setF(p => ({ ...p, phoneNumber: e.target.value.replace(/\D/g, "").slice(0, 10) }));

    // ─── Account number: four boxes of 4 digits, numeric-only, auto-advance ───
    const setAcctPart = (i: number) => (e: ChangeEvent<HTMLInputElement>) => {
        const v = e.target.value.replace(/\D/g, "").slice(0, 4);   // digits only, max 4
        setAcct(prev => {
            const next = [...prev];
            next[i] = v;
            return next;
        });
        // Auto-advance to the next box once this one is full
        if (v.length === 4 && i < 3) {
            const el = document.getElementById(`acct-${i + 1}`);
            if (el) el.focus();
        }
    };
    const onAcctKeyDown = (i: number) => (e: KeyboardEvent<HTMLInputElement>) => {
        // Backspace on an empty box jumps to the previous box
        if (e.key === "Backspace" && !acct[i] && i > 0) {
            const el = document.getElementById(`acct-${i - 1}`);
            if (el) el.focus();
        }
    };
    const onAcctPaste = (e: ClipboardEvent<HTMLInputElement>) => {
        // Pasting a full 16-digit number splits it across all four boxes
        const digits = (e.clipboardData.getData("text") || "").replace(/\D/g, "").slice(0, 16);
        if (!digits) return;
        e.preventDefault();
        setAcct([digits.slice(0, 4), digits.slice(4, 8), digits.slice(8, 12), digits.slice(12, 16)]);
        const focusIdx = Math.min(Math.floor(digits.length / 4), 3);
        const el = document.getElementById(`acct-${focusIdx}`);
        if (el) el.focus();
    };

    const submit = async (ev: FormEvent) => {
        ev.preventDefault(); setErr(""); setSuccess("");
        if (Object.values(f).some(v => !v)) { setErr("All fields are required"); return; }
        // India only: exactly 10 digits, submitted with the +91 country code
        if (!/^\d{10}$/.test(f.phoneNumber)) { setErr("Enter a valid 10-digit Indian phone number."); return; }
        // Date of birth cannot be in the future
        if (f.dateOfBirth > todayISO()) { setErr("Date of birth cannot be in the future."); return; }
        // Account number: concatenate the four boxes and require exactly 16 digits
        const accountNumber = acct.join("");
        if (!/^\d{16}$/.test(accountNumber)) { setErr("Account number must be exactly 16 digits (4 digits per box)."); return; }
        setLoading(true);
        try {
            // Create the account only — do NOT store a token or auto-login.
            await api("/auth/register", { method: "POST", body: JSON.stringify({ ...f, phoneNumber: `+91${f.phoneNumber}`, accountNumber }) });
            setLoading(false);
            setSuccess("Account created successfully.");
            // Redirect to the Login page after a short delay (no Payments redirect).
            setTimeout(() => onSwitch(), 1200);
        } catch (e) { setErr(errMessage(e)); setLoading(false); }
    };

    return (
        <form onSubmit={submit} style={{ display:"flex", flexDirection:"column", gap:14 }}>
            {err && <div style={S.error}>{err}</div>}
            {success && <div style={S.success}>{success}</div>}
            <div style={S.row}>
                <Field label="First Name"><input style={S.input} value={f.firstName} onChange={set("firstName")} placeholder="Jane" /></Field>
                <Field label="Last Name"><input style={S.input} value={f.lastName} onChange={set("lastName")} placeholder="Smith" /></Field>
            </div>
            <Field label="Username"><input style={S.input} value={f.username} onChange={set("username")} placeholder="jsmith42" /></Field>
            <Field label="Email"><input style={S.input} type="email" value={f.email} onChange={set("email")} placeholder="jane@example.com" /></Field>
            <Field label="Password">
                <div style={{ position:"relative" }}>
                    <input style={{ ...S.input, paddingRight:40 }} type={showPw?"text":"password"} value={f.password} onChange={set("password")} placeholder="Create a strong password" />
                    <button type="button" onClick={() => setShowPw(p=>!p)} style={{ position:"absolute", right:12, top:"50%", transform:"translateY(-50%)", background:"none", border:"none", color:C.muted, cursor:"pointer", padding:0, display:"flex" }}>
                        <EyeIcon open={showPw} />
                    </button>
                </div>
                <PwStrength pw={f.password} />
            </Field>
            <div style={S.row}>
                <Field label="Phone">
                    <div style={{ display:"flex", alignItems:"center", gap:8 }}>
                        <span style={{ display:"flex", alignItems:"center", padding:"10px 12px", background:"#f5f3ff", border:`1px solid ${C.inputBorder}`, borderRadius:10, color:C.muted, fontSize:14, whiteSpace:"nowrap" }}>+91</span>
                        <input style={S.input} type="tel" inputMode="numeric" maxLength={10} value={f.phoneNumber} onChange={setPhone} placeholder="9876543210" />
                    </div>
                </Field>
                <Field label="Date of Birth"><input style={S.input} type="date" value={f.dateOfBirth} onChange={set("dateOfBirth")} max={todayISO()} /></Field>
            </div>
            <Field label="Account Number (16 digits)">
                <div style={{ display:"flex", alignItems:"center", gap:8 }}>
                    {[0,1,2,3].map(i => (
                        <div key={i} style={{ display:"flex", alignItems:"center", gap:8, flex:1 }}>
                            <input
                                id={`acct-${i}`}
                                style={{ ...S.input, textAlign:"center", letterSpacing:"0.15em" }}
                                type="text"
                                inputMode="numeric"
                                maxLength={4}
                                value={acct[i]}
                                onChange={setAcctPart(i)}
                                onKeyDown={onAcctKeyDown(i)}
                                onPaste={onAcctPaste}
                                placeholder="0000"
                                aria-label={`Account number group ${i + 1} of 4`}
                            />
                            {i < 3 && <span style={{ color:C.faint, fontWeight:700 }}>-</span>}
                        </div>
                    ))}
                </div>
            </Field>
            <Btn loading={loading} disabled={!!success}>Register</Btn>
            <p style={{ textAlign:"center", ...S.muted, margin:0 }}>Already have an account? <button type="button" onClick={onSwitch} style={{ background:"none", border:"none", color:C.accentDeep, fontWeight:600, cursor:"pointer", fontSize:13 }}>Sign in</button></p>
        </form>
    );
}

function AuthPage({ onSuccess }: { onSuccess: (user: UserInfo) => void }) {
    const [mode, setMode] = useState<"login" | "register">("login");
    return (
        <div style={{ ...S.page, display:"flex", alignItems:"center", justifyContent:"center", padding:"32px 16px" }}>
            <div style={{ width:"100%", maxWidth:440 }}>
                <div style={{ textAlign:"center", marginBottom:32 }}>
                    <div style={{ fontSize:28, fontWeight:800, letterSpacing:"-0.03em" }}>Pay<span style={S.teal}>Flow</span></div>
                    <p style={S.muted}>Secure payment infrastructure</p>
                </div>
                <div style={S.card}>
                    <div style={{ display:"flex", background:"#f5f3ff", borderRadius:10, padding:3, marginBottom:24, border:`1px solid ${C.line}` }}>
                        {(["login","register"] as const).map(m => (
                            <button key={m} onClick={() => setMode(m)} style={{ flex:1, padding:"8px", borderRadius:7, border:"none", background: mode===m ? "rgba(167,139,250,0.18)" : "transparent", color: mode===m ? C.accentDeep : C.muted, fontWeight:600, fontSize:13, cursor:"pointer", outline: mode===m ? "1px solid rgba(167,139,250,0.35)" : "none" }}>
                                {m === "login" ? "Sign in" : "Register"}
                            </button>
                        ))}
                    </div>
                    {mode === "login"
                        ? <LoginForm onSuccess={onSuccess} onSwitch={() => setMode("register")} />
                        : <RegisterForm onSwitch={() => setMode("login")} />}
                </div>
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// PAYMENT WORKFLOW — 3 screens: Details → Review → Confirmation
// ═══════════════════════════════════════════════════════════════════

interface PaymentDetailsProps {
    initial: PaymentFormData | null;
    accounts: Account[];
    payees: Payee[];
    onNext: (form: PaymentFormData, meta: PaymentMeta) => void;
    onCancel: () => void;
}
function PaymentDetails({ initial, accounts, payees, onNext, onCancel }: PaymentDetailsProps) {
    const [f, setF] = useState({
        accountId: initial ? String(initial.accountId) : "",
        payeeId: initial ? String(initial.payeeId) : "",
        paymentAmount: initial ? String(initial.paymentAmount) : "",
        paymentDate: initial ? initial.paymentDate : todayISO(),
        memo: initial ? initial.memo : "",
    });
    const [fee, setFee] = useState<FeePreview | null>(null);
    const [err, setErr] = useState<Record<string, string>>({});
    const [apiErr, setApiErr] = useState("");
    const [feeLoading, setFeeLoading] = useState(false);
    const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(p => ({ ...p, [k]: e.target.value }));

    const fetchFee = useCallback(async (amount: string) => {
        if (!amount || isNaN(Number(amount)) || +amount <= 0) { setFee(null); return; }
        setFeeLoading(true);
        try {
            const data = await api<FeePreview>(`/payment/fee?amount=${amount}`);
            setFee(data);
        } catch { setFee(null); } finally { setFeeLoading(false); }
    }, []);

    useEffect(() => {
        const t = setTimeout(() => fetchFee(f.paymentAmount), 500);
        return () => clearTimeout(t);
    }, [f.paymentAmount, fetchFee]);

    const validate = (): Record<string, string> => {
        const e: Record<string, string> = {};
        if (!f.accountId) e.accountId = "Select a from account";
        if (!f.payeeId) e.payeeId = "Select a payee";
        if (!f.paymentAmount || +f.paymentAmount <= 0) e.paymentAmount = "Enter a valid amount";
        if (!f.paymentDate) e.paymentDate = "Select a payment date";
        else if (f.paymentDate < todayISO()) e.paymentDate = "Date cannot be in the past";
        if (f.memo && f.memo.length > 100) e.memo = "Memo must be under 100 characters";
        return e;
    };

    const submit = () => {
        const e = validate();
        if (Object.keys(e).length) { setErr(e); return; }
        if (!fee) { setApiErr("Could not calculate fee. Check the amount."); return; }
        const account = accounts.find(a => a.accountId === +f.accountId);
        const payee = payees.find(p => p.payeeId === +f.payeeId);
        if (!account || !payee) { setApiErr("Selected account or payee is no longer available."); return; }
        onNext(
            { accountId: +f.accountId, payeeId: +f.payeeId, paymentAmount: +f.paymentAmount, paymentDate: f.paymentDate, memo: f.memo },
            { fee, account, payee },
        );
    };

    return (
        <div>
            <h2 style={{ margin:"0 0 6px", fontSize:20, fontWeight:700, color:C.text }}>Payment Details</h2>
            <p style={{ ...S.muted, margin:"0 0 24px" }}>Step 1 of 3 — Enter payment information</p>
            {apiErr && <div style={S.error}>{apiErr}</div>}
            <div style={{ display:"flex", flexDirection:"column", gap:16 }}>
                <Field label="From Account *" error={err.accountId}>
                    <select style={S.select} value={f.accountId} onChange={set("accountId")}>
                        <option value="">Select account</option>
                        {accounts.map(a => <option key={a.accountId} value={a.accountId}>{a.accountName} — {maskAccount(a.accountNumber)} ({fmt(a.accountBalance)})</option>)}
                    </select>
                </Field>
                <Field label="To (Payee) *" error={err.payeeId}>
                    <select style={S.select} value={f.payeeId} onChange={set("payeeId")}>
                        <option value="">Select payee</option>
                        {payees.map(p => <option key={p.payeeId} value={p.payeeId}>{p.payeeName} — {p.payeeNumber}</option>)}
                    </select>
                </Field>
                <Field label="Payment Amount (₹) *" error={err.paymentAmount}>
                    <input style={S.input} type="number" min="0.01" step="0.01" value={f.paymentAmount} onChange={set("paymentAmount")} placeholder="0.00" />
                </Field>
                {(fee || feeLoading) && (
                    <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr 1fr", gap:10 }}>
                        {[["Payment", fee ? fmt(fee.paymentAmount) : "…"], ["Fee", fee ? fmt(fee.feeAmount) : "…"], ["Total", fee ? fmt(fee.totalAmount) : "…"]].map(([l,v]) => (
                            <div key={l} style={{ background:"rgba(167,139,250,0.1)", border:"1px solid rgba(167,139,250,0.25)", borderRadius:10, padding:"10px 14px" }}>
                                <div style={{ fontSize:11, color:C.muted, marginBottom:4 }}>{l}</div>
                                <div style={{ fontSize:15, fontWeight:700, color:C.accentDeep }}>{v}</div>
                            </div>
                        ))}
                    </div>
                )}
                <Field label="Payment Date (DD/MM/YYYY) *" error={err.paymentDate}>
                    <input style={S.input} type="date" value={f.paymentDate} onChange={set("paymentDate")} min={todayISO()} />
                </Field>
                <Field label="Memo (optional)" error={err.memo}>
                    <input style={S.input} value={f.memo} onChange={set("memo")} placeholder="Optional note (max 100 chars)" maxLength={100} />
                    <span style={{ fontSize:11, color:C.faint, marginTop:2 }}>{(f.memo||"").length}/100</span>
                </Field>
                <div style={{ display:"flex", gap:12, marginTop:8 }}>
                    <Btn onClick={submit}>Review Payment →</Btn>
                    <Btn ghost onClick={onCancel}>Cancel</Btn>
                </div>
            </div>
        </div>
    );
}

interface PaymentReviewProps {
    formData: PaymentFormData;
    meta: PaymentMeta;
    onConfirm: () => void;
    onEdit: () => void;
    loading: boolean;
}
function PaymentReview({ formData, meta, onConfirm, onEdit, loading }: PaymentReviewProps) {
    const rows: [string, string][] = [
        ["From Account", `${meta.account.accountName} (${maskAccount(meta.account.accountNumber)})`],
        ["To Payee", `${meta.payee.payeeName} (${meta.payee.payeeNumber})`],
        ["Payment Date", fmtDate(formData.paymentDate)],
        ["Payment Amount", fmt(formData.paymentAmount)],
        ["Transaction Fee", fmt(meta.fee.feeAmount)],
        ["Total Debit", fmt(meta.fee.totalAmount)],
        ["Memo", formData.memo || "—"],
    ];
    return (
        <div>
            <h2 style={{ margin:"0 0 6px", fontSize:20, fontWeight:700, color:C.text }}>Review Payment</h2>
            <p style={{ ...S.muted, margin:"0 0 24px" }}>Step 2 of 3 — Confirm the details before submitting</p>
            <div style={{ background:"#faf9ff", border:`1px solid ${C.line}`, borderRadius:14, padding:"20px 24px", marginBottom:20 }}>
                {rows.map(([k, v], i) => (
                    <div key={k} style={{ display:"flex", justifyContent:"space-between", padding:"10px 0", borderBottom: i < rows.length-1 ? `1px solid ${C.line}` : "none" }}>
                        <span style={{ color:C.muted, fontSize:13 }}>{k}</span>
                        <span style={{ fontWeight: k === "Total Debit" ? 700 : 500, color: k === "Total Debit" ? C.accentDeep : C.text, fontSize:14 }}>{v}</span>
                    </div>
                ))}
            </div>
            <div style={{ display:"flex", gap:12 }}>
                <Btn onClick={onConfirm} loading={loading}>Confirm & Submit</Btn>
                <Btn ghost onClick={onEdit}>← Edit</Btn>
            </div>
        </div>
    );
}

// ─── MFA: OTP verification screen ────────────────────────────────────────────
interface PaymentOtpProps {
    challenge: OtpChallenge;
    onVerified: (payment: Payment) => void;
    onBack: () => void;
    onResend: () => Promise<void>;
}
function PaymentOtp({ challenge, onVerified, onBack, onResend }: PaymentOtpProps) {
    const [otp, setOtp] = useState("");
    const [err, setErr] = useState("");
    const [loading, setLoading] = useState(false);
    const [resending, setResending] = useState(false);
    const [secondsLeft, setSecondsLeft] = useState(challenge.expiresInSeconds || 300);

    // Reset and (re)start the countdown whenever a new challenge arrives
    useEffect(() => { setSecondsLeft(challenge.expiresInSeconds || 300); setOtp(""); }, [challenge]);
    useEffect(() => {
        if (secondsLeft <= 0) return;
        const t = setInterval(() => setSecondsLeft(s => s - 1), 1000);
        return () => clearInterval(t);
    }, [secondsLeft]);

    const expired = secondsLeft <= 0;
    const mmss = `${String(Math.floor(secondsLeft / 60)).padStart(2, "0")}:${String(secondsLeft % 60).padStart(2, "0")}`;
    const setOtpVal = (e: ChangeEvent<HTMLInputElement>) => { setOtp(e.target.value.replace(/\D/g, "").slice(0, 6)); setErr(""); };

    const verify = async () => {
        setErr("");
        if (expired) { setErr("OTP has expired. Please resend a new code."); return; }
        if (!/^\d{6}$/.test(otp)) { setErr("Enter the 6-digit OTP sent to your mobile."); return; }
        setLoading(true);
        try {
            const result = await api<Payment>("/payment/verify", {
                method: "POST",
                body: JSON.stringify({ challengeId: challenge.challengeId, otp }),
            });
            onVerified(result);
        } catch (e) { setErr(errMessage(e)); setLoading(false); }
    };

    const resend = async () => {
        setErr(""); setResending(true);
        try { await onResend(); }
        catch (e) { setErr(errMessage(e)); }
        finally { setResending(false); }
    };

    return (
        <div>
            <h2 style={{ margin:"0 0 6px", fontSize:20, fontWeight:700, color:C.text }}>Verify OTP</h2>
            <p style={{ ...S.muted, margin:"0 0 24px" }}>Step 3 of 3 — Enter the code sent to your mobile</p>
            {err && <div style={S.error}>{err}</div>}
            <p style={{ ...S.muted, margin:"0 0 18px" }}>
                {challenge.message || `An OTP has been sent to ${challenge.maskedMobile}`}
            </p>
            <Field label="One-Time Password (6 digits)">
                <input
                    style={{ ...S.input, textAlign:"center", letterSpacing:"0.45em", fontSize:20, fontWeight:700 }}
                    type="text" inputMode="numeric" maxLength={6} value={otp}
                    onChange={setOtpVal}
                    onKeyDown={e => { if (e.key === "Enter") verify(); }}
                    placeholder="••••••" autoFocus aria-label="One-time password"
                />
            </Field>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", margin:"12px 0 22px" }}>
                <span style={{ fontSize:12, color: expired ? "#dc2626" : C.muted }}>
                    {expired ? "Code expired" : `Code expires in ${mmss}`}
                </span>
                <button type="button" onClick={resend} disabled={resending}
                        style={{ background:"none", border:"none", color:C.accentDeep, fontWeight:600, cursor:"pointer", fontSize:13, opacity: resending ? 0.6 : 1 }}>
                    {resending ? "Sending…" : "Resend OTP"}
                </button>
            </div>
            <div style={{ display:"flex", gap:12 }}>
                <Btn onClick={verify} loading={loading} disabled={expired}>Verify &amp; Pay</Btn>
                <Btn ghost onClick={onBack}>← Back</Btn>
            </div>
        </div>
    );
}

interface PaymentConfirmationProps {
    paymentId: number | null;
    onNewPayment: () => void;
    onViewAll: () => void;
}
function PaymentConfirmation({ paymentId, onNewPayment, onViewAll }: PaymentConfirmationProps) {
    return (
        <div style={{ textAlign:"center" }}>
            <div style={{ width:64, height:64, borderRadius:"50%", background:"rgba(16,185,129,0.14)", border:`2px solid ${C.done}`, display:"flex", alignItems:"center", justifyContent:"center", margin:"0 auto 20px", fontSize:28, color:"#059669" }}>✓</div>
            <h2 style={{ margin:"0 0 6px", fontSize:22, fontWeight:700, color:C.accentDeep }}>Payment Submitted!</h2>
            <p style={{ ...S.muted, margin:"0 0 6px" }}>Your transaction has been recorded.</p>
            <div style={{ display:"inline-block", background:"#f5f3ff", border:`1px solid ${C.inputBorder}`, borderRadius:10, padding:"8px 20px", margin:"16px 0 28px", fontSize:13, color:C.text }}>
                Transaction ID: <strong style={{ color:C.accentDeep }}>#{paymentId}</strong>
            </div>
            <div style={{ display:"flex", gap:12, justifyContent:"center" }}>
                <Btn onClick={onNewPayment}>Make Another Payment</Btn>
                <Btn ghost onClick={onViewAll}>View All Payments</Btn>
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// PAYMENT LIST
// ═══════════════════════════════════════════════════════════════════

interface PaymentListProps {
    onNew: () => void;
    onProfile: () => void;
    onLogout: () => void;
}
function PaymentList({ onNew, onProfile, onLogout }: PaymentListProps) {
    const [payments, setPayments] = useState<Payment[]>([]);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState("");
    const [deleting, setDeleting] = useState<number | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        try { setPayments(await api<Payment[]>("/payment")); }
        catch (e) { setErr(errMessage(e)); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const del = async (id: number) => {
        if (!window.confirm("Delete this payment?")) return;
        setDeleting(id);
        try { await api(`/${id}/payment`, { method: "DELETE" }); setPayments(p => p.filter(x => x.paymentId !== id)); }
        catch (e) { alert(errMessage(e)); }
        finally { setDeleting(null); }
    };

    const statusColor = (s: string) => s === "COMPLETED" ? C.done : s === "CANCELLED" ? C.cancelled : C.pending;

    return (
        <div>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:28 }}>
                <div>
                    <div style={{ fontSize:24, fontWeight:800, letterSpacing:"-0.02em", color:C.text }}>Pay<span style={S.teal}>Flow</span></div>
                    <div style={S.muted}>Payment History</div>
                </div>
                <div style={{ display:"flex", gap:10 }}>
                    <Btn onClick={onNew}>+ New Payment</Btn>
                    <Btn ghost onClick={onProfile}>Profile</Btn>
                    <Btn ghost onClick={onLogout}>Sign out</Btn>
                </div>
            </div>

            {err && <div style={S.error}>{err}</div>}

            {loading ? (
                <div style={{ textAlign:"center", padding:"48px 0", color:C.muted }}><Spin /> Loading payments…</div>
            ) : payments.length === 0 ? (
                <div style={{ ...S.card, textAlign:"center", padding:"48px" }}>
                    <div style={{ fontSize:40, marginBottom:12 }}>💸</div>
                    <div style={{ color:C.muted }}>No payments yet. Make your first payment!</div>
                </div>
            ) : (
                <div style={{ display:"grid", gridTemplateColumns:"repeat(auto-fill, minmax(420px, 1fr))", gap:14 }}>
                    {payments.map(p => (
                        <div key={p.paymentId} style={{ ...S.card, padding:"16px 22px", display:"flex", alignItems:"center", gap:16 }}>
                            <div style={{ flex:1 }}>
                                <div style={{ fontWeight:600, fontSize:14, color:C.text }}>{p.payeeName}</div>
                                <div style={{ ...S.muted, marginTop:2 }}>{p.accountName} → {p.payeeNumber} &nbsp;·&nbsp; {fmtDate(p.paymentDate)}</div>
                                {p.memo && <div style={{ fontSize:12, color:C.faint, marginTop:2 }}>"{p.memo}"</div>}
                            </div>
                            <div style={{ textAlign:"right" }}>
                                <div style={{ fontWeight:700, fontSize:16, color:C.accentDeep }}>{fmt(p.paymentAmount)}</div>
                                <div style={{ fontSize:11, color:C.muted }}>Fee: {fmt(p.feeAmount)}</div>
                            </div>
                            <div>
                <span style={{ padding:"3px 10px", borderRadius:20, fontSize:11, fontWeight:600, background:`${statusColor(p.status)}22`, color:statusColor(p.status), border:`1px solid ${statusColor(p.status)}55` }}>
                  {p.status}
                </span>
                            </div>
                            {p.status !== "COMPLETED" && (
                                <button onClick={() => del(p.paymentId)} disabled={deleting === p.paymentId}
                                        style={{ background:"#fee2e2", border:"1px solid #fecaca", borderRadius:8, color:"#dc2626", fontSize:12, fontWeight:600, cursor:"pointer", padding:"5px 12px" }}>
                                    {deleting === p.paymentId ? "…" : "Delete"}
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// PROFILE — view & edit the current user's details
// ═══════════════════════════════════════════════════════════════════

interface ProfilePageProps {
    user: UserInfo;
    onSaved: (user: UserInfo) => void;
    onBack: () => void;
}
function ProfilePage({ user, onSaved, onBack }: ProfilePageProps) {
    // The backend stores phone as +91XXXXXXXXXX; edit just the 10 local digits.
    const [f, setF] = useState({
        firstName: user.firstName ?? "",
        lastName: user.lastName ?? "",
        email: user.email ?? "",
        phone: (user.phoneNumber ?? "").replace(/^\+91/, ""),
    });
    const [err, setErr] = useState("");
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);
    const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement>) => { setF(p => ({ ...p, [k]: e.target.value })); setSuccess(""); };
    const setPhone = (e: ChangeEvent<HTMLInputElement>) => { setF(p => ({ ...p, phone: e.target.value.replace(/\D/g, "").slice(0, 10) })); setSuccess(""); };

    const submit = async () => {
        setErr(""); setSuccess("");
        if (!f.firstName || !f.lastName || !f.email) { setErr("First name, last name and email are required."); return; }
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email)) { setErr("Enter a valid email address."); return; }
        if (!/^\d{10}$/.test(f.phone)) { setErr("Enter a valid 10-digit Indian phone number."); return; }
        setLoading(true);
        try {
            const payload: ProfileUpdate = {
                firstName: f.firstName,
                lastName: f.lastName,
                email: f.email,
                phoneNumber: `+91${f.phone}`,
            };
            const updated = await api<UserInfo>("/auth/profile", { method: "PUT", body: JSON.stringify(payload) });
            onSaved(updated);
            setSuccess("Profile updated successfully.");
        } catch (e) { setErr(errMessage(e)); }
        finally { setLoading(false); }
    };

    return (
        <div>
            <button onClick={onBack} style={{ background:"none", border:"none", color:C.muted, cursor:"pointer", fontSize:13, marginBottom:20, display:"flex", alignItems:"center", gap:5 }}>
                ← Back to payments
            </button>
            <div style={{ maxWidth:560, margin:"0 auto" }}>
                <div style={S.card}>
                    <h2 style={{ margin:"0 0 6px", fontSize:20, fontWeight:700, color:C.text }}>Your Profile</h2>
                    <p style={{ ...S.muted, margin:"0 0 24px" }}>Update your name and contact details</p>
                    {err && <div style={S.error}>{err}</div>}
                    {success && <div style={{ ...S.success, marginBottom:14 }}>{success}</div>}
                    <div style={{ display:"flex", flexDirection:"column", gap:16 }}>
                        <div style={S.row}>
                            <Field label="First Name"><input style={S.input} value={f.firstName} onChange={set("firstName")} placeholder="Jane" /></Field>
                            <Field label="Last Name"><input style={S.input} value={f.lastName} onChange={set("lastName")} placeholder="Smith" /></Field>
                        </div>
                        <Field label="Username">
                            <input style={{ ...S.input, background:"#f1eefb", color:C.muted, cursor:"not-allowed" }} value={user.username} disabled />
                        </Field>
                        <Field label="Email"><input style={S.input} type="email" value={f.email} onChange={set("email")} placeholder="jane@example.com" /></Field>
                        <Field label="Phone">
                            <div style={{ display:"flex", alignItems:"center", gap:8 }}>
                                <span style={{ display:"flex", alignItems:"center", padding:"10px 12px", background:"#f5f3ff", border:`1px solid ${C.inputBorder}`, borderRadius:10, color:C.muted, fontSize:14, whiteSpace:"nowrap" }}>+91</span>
                                <input style={S.input} type="tel" inputMode="numeric" maxLength={10} value={f.phone} onChange={setPhone} placeholder="9876543210" />
                            </div>
                        </Field>
                        <div style={{ display:"flex", gap:12, marginTop:8 }}>
                            <Btn onClick={submit} loading={loading}>Save Changes</Btn>
                            <Btn ghost onClick={onBack}>Cancel</Btn>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// PAYMENT FLOW ORCHESTRATOR
// ═══════════════════════════════════════════════════════════════════

type Screen = "details" | "review" | "otp" | "confirmation";

function PaymentFlow({ onDone }: { onDone: () => void }) {
    const [screen, setScreen] = useState<Screen>("details");
    const [formData, setFormData] = useState<PaymentFormData | null>(null);
    const [meta, setMeta] = useState<PaymentMeta | null>(null);
    const [challenge, setChallenge] = useState<OtpChallenge | null>(null);   // OTP challenge from /payment/initiate
    const [confirmedId, setConfirmedId] = useState<number | null>(null);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [payees, setPayees] = useState<Payee[]>([]);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        Promise.all([api<Account[]>("/accounts"), api<Payee[]>("/payees")])
            .then(([a, p]) => { setAccounts(a); setPayees(p); })
            .catch(console.error);
    }, []);

    const handleNext = (fd: PaymentFormData, m: PaymentMeta) => { setFormData(fd); setMeta(m); setScreen("review"); };
    const handleEdit = () => setScreen("details");

    // Builds the payment payload from the collected form data
    const buildPayload = () => ({
        accountId: formData!.accountId,
        payeeId: formData!.payeeId,
        paymentAmount: formData!.paymentAmount,
        paymentDate: formData!.paymentDate,
        memo: formData!.memo || null,
    });

    // MFA step 1: validate the payment and trigger an OTP to the user's mobile
    const handleConfirm = async () => {
        setSubmitting(true);
        try {
            const ch = await api<OtpChallenge>("/payment/initiate", { method: "POST", body: JSON.stringify(buildPayload()) });
            setChallenge(ch);
            setScreen("otp");
        } catch (e) { alert(errMessage(e)); }
        finally { setSubmitting(false); }
    };

    // Resend: request a fresh OTP/challenge for the same payment
    const handleResendOtp = async () => {
        const ch = await api<OtpChallenge>("/payment/initiate", { method: "POST", body: JSON.stringify(buildPayload()) });
        setChallenge(ch);
    };

    // MFA step 2 succeeded: the payment was created on the server
    const handleOtpVerified = (result: Payment) => {
        setConfirmedId(result.paymentId);
        setChallenge(null);
        setScreen("confirmation");
    };

    if (screen === "confirmation") {
        return (
            <div style={S.card}>
                <PaymentConfirmation paymentId={confirmedId} onNewPayment={() => { setScreen("details"); setFormData(null); setChallenge(null); }} onViewAll={onDone} />
            </div>
        );
    }

    return (
        <div>
            <button onClick={onDone} style={{ background:"none", border:"none", color:C.muted, cursor:"pointer", fontSize:13, marginBottom:20, display:"flex", alignItems:"center", gap:5 }}>
                ← Back to payments
            </button>
            <div style={{ display:"flex", gap:8, marginBottom:28, alignItems:"center" }}>
                {["Details","Review","Verify"].map((lbl, i) => {
                    const step = i + 1;
                    const currentStep = screen === "details" ? 1 : screen === "review" ? 2 : 3;
                    const active = currentStep === step;
                    const done = currentStep > step;
                    return (
                        <div key={lbl} style={{ display:"flex", alignItems:"center", gap:8 }}>
                            <div style={{ width:26, height:26, borderRadius:"50%", background: done ? C.accent : active ? "rgba(167,139,250,0.18)" : "#f0ecf9", border: `2px solid ${done||active ? C.accent : C.inputBorder}`, display:"flex", alignItems:"center", justifyContent:"center", fontSize:12, fontWeight:700, color: done ? "#ffffff" : active ? C.accentDeep : C.faint }}>
                                {done ? "✓" : step}
                            </div>
                            <span style={{ fontSize:13, color: active ? C.text : C.faint, fontWeight: active ? 600 : 400 }}>{lbl}</span>
                            {i < 2 && <div style={{ width:32, height:1, background:C.inputBorder }} />}
                        </div>
                    );
                })}
            </div>

            <div style={S.card}>
                {screen === "details" && <PaymentDetails initial={formData} accounts={accounts} payees={payees} onNext={handleNext} onCancel={onDone} />}
                {screen === "review" && formData && meta && <PaymentReview formData={formData} meta={meta} onConfirm={handleConfirm} onEdit={handleEdit} loading={submitting} />}
                {screen === "otp" && challenge && <PaymentOtp challenge={challenge} onVerified={handleOtpVerified} onBack={() => setScreen("review")} onResend={handleResendOtp} />}
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// APP ROOT
// ═══════════════════════════════════════════════════════════════════

export default function App() {
    const [user, setUser] = useState<UserInfo | null>(null);
    const [view, setView] = useState<"list" | "newPayment" | "profile">("list");
    const [bootstrapping, setBootstrapping] = useState(true);
    const [idleNotice, setIdleNotice] = useState(false);

    // Restore the session on load: the httpOnly cookie (if present and valid)
    // lets /auth/me return the current user without any token in JS.
    useEffect(() => {
        api<UserInfo>("/auth/me")
            .then(u => setUser(u))
            .catch(() => setUser(null))
            .finally(() => setBootstrapping(false));
    }, []);

    const logout = useCallback(async () => {
        try { await api("/auth/logout", { method: "POST" }); }
        catch { /* clearing local state regardless */ }
        setUser(null);
        setView("list");
    }, []);

    // Sign the user out after 15 min of inactivity (matches the server-enforced
    // idle window) and surface a notice on the login screen explaining why.
    const handleIdle = useCallback(() => {
        setIdleNotice(true);
        void logout();
    }, [logout]);
    useIdleTimeout(!!user && !bootstrapping, handleIdle);

    if (bootstrapping) {
        return (
            <div style={{ ...S.page, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <div style={{ color: C.muted, display: "flex", alignItems: "center", gap: 8 }}><Spin /> Loading…</div>
            </div>
        );
    }

    return (
        <>
            <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        html, body, #root { height: 100%; }
        body { margin: 0; background: #f3f0fb; }
        @keyframes spin { to { transform: rotate(360deg); } }
        input::placeholder { color: #b8b3cc; }
        select option { background: #ffffff; color: #3f3d56; }
        ::-webkit-scrollbar { width: 8px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: rgba(167,139,250,0.35); border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: rgba(167,139,250,0.55); }
        input:focus, select:focus { border-color: #a78bfa !important; box-shadow: 0 0 0 3px rgba(167,139,250,0.18); }
      `}</style>

            {!user ? (
                <>
                    {idleNotice && (
                        <div style={{
                            position: "fixed", top: 0, left: 0, right: 0, zIndex: 1000,
                            background: "#fef3c7", color: "#92400e",
                            borderBottom: "1px solid #fcd34d",
                            padding: "10px 16px", textAlign: "center", fontSize: 13, fontWeight: 600,
                        }}>
                            You were signed out after 15 minutes of inactivity. Please sign in again.
                        </div>
                    )}
                    <AuthPage onSuccess={u => { setIdleNotice(false); setUser(u); setView("list"); }} />
                </>
            ) : (
                <div style={S.page}>
                    <div style={{ width:"100%", maxWidth:1440, margin:"0 auto", padding:"32px 40px" }}>
                        {view === "list" && (
                            <PaymentList onNew={() => setView("newPayment")} onProfile={() => setView("profile")} onLogout={logout} />
                        )}
                        {view === "newPayment" && <PaymentFlow onDone={() => setView("list")} />}
                        {view === "profile" && (
                            <ProfilePage user={user} onSaved={u => setUser(u)} onBack={() => setView("list")} />
                        )}
                    </div>
                </div>
            )}
        </>
    );
}
