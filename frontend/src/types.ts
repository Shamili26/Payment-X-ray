// Shared types mirroring the backend DTOs (see PaymentDto.java / Auth.java).

export interface UserInfo {
    userId: number;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    phoneNumber?: string;
    role: string;
}

// Request body for PUT /auth/profile (editable profile fields only)
export interface ProfileUpdate {
    firstName: string;
    lastName: string;
    email: string;
    phoneNumber: string;
}

export interface AuthResponse {
    // The JWT is delivered as an httpOnly cookie and is NOT exposed to JS,
    // so accessToken is intentionally absent/null in the response body.
    accessToken?: string | null;
    tokenType: string;
    expiresIn: number;
    user: UserInfo;
}

export interface Account {
    accountId: number;
    accountNumber: string;
    accountName: string;
    accountBalance: number;
    accountStatus: string;
}

export interface Payee {
    payeeId: number;
    payeeNumber: string;
    payeeName: string;
    amountDue: number;
    dueDate: string;
}

export interface FeePreview {
    paymentAmount: number;
    feeAmount: number;
    totalAmount: number;
}

export interface Payment {
    paymentId: number;
    accountId: number;
    accountName: string;
    accountNumber: string;
    payeeId: number;
    payeeName: string;
    payeeNumber: string;
    paymentAmount: number;
    feeAmount: number;
    paymentDate: string;
    memo: string | null;
    status: string;
    updatedDatetime: string;
}

// Response returned by POST /payment/initiate (MFA step 1)
export interface OtpChallenge {
    challengeId: string;
    maskedMobile: string;
    expiresInSeconds: number;
    message: string;
}

// Collected payment form data carried between flow screens
export interface PaymentFormData {
    accountId: number;
    payeeId: number;
    paymentAmount: number;
    paymentDate: string;
    memo: string;
}

// Extra metadata (resolved entities + fee) shown on the Review screen
export interface PaymentMeta {
    fee: FeePreview;
    account: Account;
    payee: Payee;
}

// Shape of the JSON error body returned by the backend
export interface ApiError {
    status: number;
    error: string;
    message: string;
    timestamp: string;
}

