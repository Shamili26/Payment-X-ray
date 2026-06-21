package com.paymentapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentapp.config.TestSecurityConfig;
import com.paymentapp.dto.PaymentDto;
import com.paymentapp.repository.UserSessionRepository;
import com.paymentapp.security.JwtService;
import com.paymentapp.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(TestSecurityConfig.class)
@DisplayName("PaymentController Integration Tests")
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private PaymentService paymentService;

    // Collaborators of the JwtAuthenticationFilter that @WebMvcTest loads as a
    // servlet Filter. Mocked so the slice context can start.
    @MockBean  private JwtService            jwtService;
    @MockBean  private UserDetailsService    userDetailsService;
    @MockBean  private UserSessionRepository userSessionRepository;

    private PaymentDto.PaymentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new PaymentDto.PaymentResponse();
        sampleResponse.setPaymentId(1L);
        sampleResponse.setAccountId(1L);
        sampleResponse.setAccountName("Savings Account");
        sampleResponse.setAccountNumber("ACC-001");
        sampleResponse.setPayeeId(1L);
        sampleResponse.setPayeeName("Airtel Mobile");
        sampleResponse.setPayeeNumber("PAY-001");
        sampleResponse.setPaymentAmount(new BigDecimal("500"));
        sampleResponse.setFeeAmount(new BigDecimal("25"));
        sampleResponse.setPaymentDate(LocalDate.now().plusDays(1));
        sampleResponse.setStatus("PENDING");
    }

    // ─── GET /api/payment ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/payment returns 200 with list")
    void listAll_returns200WithPayments() throws Exception {
        when(paymentService.findAll()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(1))
                .andExpect(jsonPath("$[0].payeeName").value("Airtel Mobile"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/payment returns empty array when no payments")
    void listAll_noPayments_returnsEmptyArray() throws Exception {
        when(paymentService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── GET /api/{id}/payment ────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/1/payment returns 200 with payment")
    void getOne_existingId_returns200() throws Exception {
        when(paymentService.findById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/1/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.accountName").value("Savings Account"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/99/payment returns 400 when not found")
    void getOne_notFound_returns400() throws Exception {
        when(paymentService.findById(99L))
                .thenThrow(new IllegalArgumentException("Payment not found: 99"));

        mockMvc.perform(get("/api/99/payment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment not found: 99"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/2/payment returns 403 when payment belongs to another user")
    void getOne_otherUsersPayment_returns403() throws Exception {
        when(paymentService.findById(2L))
                .thenThrow(new AccessDeniedException(
                        "Payment does not belong to the current user or does not exist: 2"));

        mockMvc.perform(get("/api/2/payment"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // ─── POST /api/payment is intentionally NOT exposed (MFA cannot be bypassed) ─

    @Test
    @WithMockUser
    @DisplayName("POST /api/payment is not available — payments require the OTP flow")
    void create_directEndpoint_isNotAllowed() throws Exception {
        PaymentDto.CreateRequest req = new PaymentDto.CreateRequest();
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("500"));
        req.setPaymentDate(LocalDate.now().plusDays(1));

        // No POST handler is mapped to /api/payment (only GET and PUT), so the
        // direct create path is gone — the OTP flow is the only way to pay.
        mockMvc.perform(post("/api/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isMethodNotAllowed());
    }

    // ─── PUT /api/payment ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("PUT /api/payment returns 200 with updated payment")
    void update_validRequest_returns200() throws Exception {
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(1L);
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("800"));
        req.setPaymentDate(LocalDate.now().plusDays(2));

        when(paymentService.update(any())).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/payment returns 409 for completed payment")
    void update_completedPayment_returns409() throws Exception {
        PaymentDto.UpdateRequest req = new PaymentDto.UpdateRequest();
        req.setPaymentId(1L);
        req.setAccountId(1L);
        req.setPayeeId(1L);
        req.setPaymentAmount(new BigDecimal("500"));
        req.setPaymentDate(LocalDate.now().plusDays(1));

        when(paymentService.update(any()))
                .thenThrow(new IllegalStateException("Completed payments cannot be modified"));

        mockMvc.perform(put("/api/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Completed payments cannot be modified"));
    }

    // ─── DELETE /api/{id}/payment ─────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/1/payment returns 204")
    void delete_existingId_returns204() throws Exception {
        doNothing().when(paymentService).delete(1L);

        mockMvc.perform(delete("/api/1/payment"))
                .andExpect(status().isNoContent());

        verify(paymentService).delete(1L);
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE completed payment returns 409")
    void delete_completedPayment_returns409() throws Exception {
        doThrow(new IllegalStateException("Completed payments cannot be deleted"))
                .when(paymentService).delete(1L);

        mockMvc.perform(delete("/api/1/payment"))
                .andExpect(status().isConflict());
    }

    // ─── GET /api/accounts ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/accounts returns account list")
    void listAccounts_returns200() throws Exception {
        PaymentDto.AccountResponse acc = new PaymentDto.AccountResponse();
        acc.setAccountId(1L);
        acc.setAccountName("Savings");
        acc.setAccountNumber("ACC-001");
        acc.setAccountBalance(new BigDecimal("50000"));
        acc.setAccountStatus("ACTIVE");

        when(paymentService.findActiveAccounts()).thenReturn(List.of(acc));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName").value("Savings"))
                .andExpect(jsonPath("$[0].accountStatus").value("ACTIVE"));
    }

    // ─── GET /api/payees ──────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/payees returns payee list")
    void listPayees_returns200() throws Exception {
        PaymentDto.PayeeResponse payee = new PaymentDto.PayeeResponse();
        payee.setPayeeId(1L);
        payee.setPayeeName("Airtel Mobile");
        payee.setPayeeNumber("PAY-001");

        when(paymentService.findAllPayees()).thenReturn(List.of(payee));

        mockMvc.perform(get("/api/payees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payeeName").value("Airtel Mobile"));
    }

    // ─── GET /api/payment/fee ─────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/payment/fee returns fee preview")
    void previewFee_validAmount_returnsFee() throws Exception {
        PaymentDto.FeePreviewResponse feeResp = new PaymentDto.FeePreviewResponse();
        feeResp.setPaymentAmount(new BigDecimal("500"));
        feeResp.setFeeAmount(new BigDecimal("25"));
        feeResp.setTotalAmount(new BigDecimal("525"));

        when(paymentService.previewFee(any())).thenReturn(feeResp);

        mockMvc.perform(get("/api/payment/fee").param("amount", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeAmount").value(25))
                .andExpect(jsonPath("$.totalAmount").value(525));
    }

    // ─── Unauthenticated ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payment without auth returns 401")
    void listAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/payment"))
                .andExpect(status().isUnauthorized());
    }
}
