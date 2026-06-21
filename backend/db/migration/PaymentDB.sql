--
-- PostgreSQL database dump
--

\restrict qGLY4esDleEOTC3qvDFXP0ex594UUrjV6EntNhAtCwJbEupph8RxXTeCN9CDocY

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

-- Started on 2026-06-16 14:21:57

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 5 (class 2615 OID 2200)
-- Name: public; Type: SCHEMA; Schema: -; Owner: pg_database_owner
--

CREATE SCHEMA public;


ALTER SCHEMA public OWNER TO pg_database_owner;

--
-- TOC entry 5164 (class 0 OID 0)
-- Dependencies: 5
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: pg_database_owner
--

COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 221 (class 1259 OID 16390)
-- Name: account; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.account (
    account_id bigint NOT NULL,
    account_number character varying(20) NOT NULL,
    account_name character varying(100) NOT NULL,
    account_balance numeric(15,2) DEFAULT 0.00 NOT NULL,
    account_status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    updated_datetime timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    mobile_number character varying(20),
    user_id bigint,
    CONSTRAINT account_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.account OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 16389)
-- Name: account_account_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.account_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.account_account_id_seq OWNER TO postgres;

--
-- TOC entry 5165 (class 0 OID 0)
-- Dependencies: 220
-- Name: account_account_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.account_account_id_seq OWNED BY public.account.account_id;


--
-- TOC entry 233 (class 1259 OID 16775)
-- Name: audit_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.audit_log (
    log_id bigint NOT NULL,
    user_id bigint,
    event_type character varying(50) NOT NULL,
    ip_address character varying(45),
    user_agent character varying(255),
    metadata jsonb,
    occurred_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.audit_log OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 16774)
-- Name: audit_log_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.audit_log_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.audit_log_log_id_seq OWNER TO postgres;

--
-- TOC entry 5166 (class 0 OID 0)
-- Dependencies: 232
-- Name: audit_log_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.audit_log_log_id_seq OWNED BY public.audit_log.log_id;


--
-- TOC entry 225 (class 1259 OID 16425)
-- Name: fee; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.fee (
    fee_id bigint NOT NULL,
    amount_min numeric(15,2) NOT NULL,
    amount_max numeric(15,2),
    fee_amount numeric(10,2) NOT NULL,
    updated_datetime timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.fee OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 16424)
-- Name: fee_fee_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.fee_fee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.fee_fee_id_seq OWNER TO postgres;

--
-- TOC entry 5167 (class 0 OID 0)
-- Dependencies: 224
-- Name: fee_fee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.fee_fee_id_seq OWNED BY public.fee.fee_id;


--
-- TOC entry 234 (class 1259 OID 16806)
-- Name: otp_challenge; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.otp_challenge (
    challenge_id character varying(36) NOT NULL,
    user_id bigint NOT NULL,
    otp_hash character varying(255) NOT NULL,
    mobile_number character varying(20),
    account_id bigint NOT NULL,
    payee_id bigint NOT NULL,
    payment_amount numeric(15,2) NOT NULL,
    payment_date date NOT NULL,
    memo character varying(100),
    expires_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    consumed boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.otp_challenge OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 16409)
-- Name: payee; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.payee (
    payee_id bigint NOT NULL,
    payee_number character varying(20) NOT NULL,
    payee_name character varying(100) NOT NULL,
    amount_due numeric(15,2) DEFAULT 0.00 NOT NULL,
    due_date date,
    updated_datetime timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.payee OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 16408)
-- Name: payee_payee_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.payee_payee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.payee_payee_id_seq OWNER TO postgres;

--
-- TOC entry 5168 (class 0 OID 0)
-- Dependencies: 222
-- Name: payee_payee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.payee_payee_id_seq OWNED BY public.payee.payee_id;


--
-- TOC entry 227 (class 1259 OID 16437)
-- Name: payment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.payment (
    payment_id bigint NOT NULL,
    account_id bigint NOT NULL,
    payee_id bigint NOT NULL,
    fee_id bigint NOT NULL,
    payment_amount numeric(15,2) NOT NULL,
    payment_date date NOT NULL,
    memo character varying(100),
    status character varying(10) DEFAULT 'PENDING'::character varying NOT NULL,
    idempotency_key character varying(64),
    updated_datetime timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT payment_payment_amount_check CHECK ((payment_amount > (0)::numeric)),
    CONSTRAINT payment_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.payment OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 16436)
-- Name: payment_payment_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.payment_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.payment_payment_id_seq OWNER TO postgres;

--
-- TOC entry 5169 (class 0 OID 0)
-- Dependencies: 226
-- Name: payment_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.payment_payment_id_seq OWNED BY public.payment.payment_id;


--
-- TOC entry 231 (class 1259 OID 16568)
-- Name: user_sessions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_sessions (
    session_id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    ip_address character varying(45),
    user_agent character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    last_activity_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


ALTER TABLE public.user_sessions OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 16567)
-- Name: user_sessions_session_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.user_sessions_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_sessions_session_id_seq OWNER TO postgres;

--
-- TOC entry 5170 (class 0 OID 0)
-- Dependencies: 230
-- Name: user_sessions_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.user_sessions_session_id_seq OWNED BY public.user_sessions.session_id;


--
-- TOC entry 229 (class 1259 OID 16531)
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    username character varying(50) NOT NULL,
    email character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    first_name character varying(50) NOT NULL,
    last_name character varying(50) NOT NULL,
    role character varying(20) DEFAULT 'ROLE_USER'::character varying NOT NULL,
    is_enabled boolean DEFAULT true NOT NULL,
    is_account_non_expired boolean DEFAULT true NOT NULL,
    is_account_non_locked boolean DEFAULT true NOT NULL,
    is_credentials_non_expired boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_login timestamp without time zone,
    phone_number character varying(20) NOT NULL,
    date_of_birth date NOT NULL,
    account_number character varying(16),
    CONSTRAINT chk_role CHECK (((role)::text = ANY ((ARRAY['ROLE_USER'::character varying, 'ROLE_ADMIN'::character varying, 'ROLE_MANAGER'::character varying])::text[])))
);


ALTER TABLE public.users OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 16530)
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_user_id_seq OWNER TO postgres;

--
-- TOC entry 5171 (class 0 OID 0)
-- Dependencies: 228
-- Name: users_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;


--
-- TOC entry 4928 (class 2604 OID 16393)
-- Name: account account_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account ALTER COLUMN account_id SET DEFAULT nextval('public.account_account_id_seq'::regclass);


--
-- TOC entry 4951 (class 2604 OID 16778)
-- Name: audit_log log_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.audit_log ALTER COLUMN log_id SET DEFAULT nextval('public.audit_log_log_id_seq'::regclass);


--
-- TOC entry 4935 (class 2604 OID 16428)
-- Name: fee fee_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fee ALTER COLUMN fee_id SET DEFAULT nextval('public.fee_fee_id_seq'::regclass);


--
-- TOC entry 4932 (class 2604 OID 16412)
-- Name: payee payee_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payee ALTER COLUMN payee_id SET DEFAULT nextval('public.payee_payee_id_seq'::regclass);


--
-- TOC entry 4937 (class 2604 OID 16440)
-- Name: payment payment_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment ALTER COLUMN payment_id SET DEFAULT nextval('public.payment_payment_id_seq'::regclass);


--
-- TOC entry 4948 (class 2604 OID 16571)
-- Name: user_sessions session_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_sessions ALTER COLUMN session_id SET DEFAULT nextval('public.user_sessions_session_id_seq'::regclass);


--
-- TOC entry 4940 (class 2604 OID 16534)
-- Name: users user_id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);


--
-- TOC entry 4961 (class 2606 OID 16407)
-- Name: account account_account_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_account_number_key UNIQUE (account_number);


--
-- TOC entry 4963 (class 2606 OID 16405)
-- Name: account account_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (account_id);


--
-- TOC entry 4998 (class 2606 OID 16786)
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (log_id);


--
-- TOC entry 4970 (class 2606 OID 16435)
-- Name: fee fee_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.fee
    ADD CONSTRAINT fee_pkey PRIMARY KEY (fee_id);


--
-- TOC entry 5005 (class 2606 OID 16824)
-- Name: otp_challenge otp_challenge_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.otp_challenge
    ADD CONSTRAINT otp_challenge_pkey PRIMARY KEY (challenge_id);


--
-- TOC entry 4966 (class 2606 OID 16423)
-- Name: payee payee_payee_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payee
    ADD CONSTRAINT payee_payee_number_key UNIQUE (payee_number);


--
-- TOC entry 4968 (class 2606 OID 16421)
-- Name: payee payee_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payee
    ADD CONSTRAINT payee_pkey PRIMARY KEY (payee_id);


--
-- TOC entry 4976 (class 2606 OID 16456)
-- Name: payment payment_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_idempotency_key_key UNIQUE (idempotency_key);


--
-- TOC entry 4978 (class 2606 OID 16454)
-- Name: payment payment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (payment_id);


--
-- TOC entry 4983 (class 2606 OID 16805)
-- Name: users uq_users_account_number; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_account_number UNIQUE (account_number);


--
-- TOC entry 4994 (class 2606 OID 16583)
-- Name: user_sessions user_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_pkey PRIMARY KEY (session_id);


--
-- TOC entry 4996 (class 2606 OID 16585)
-- Name: user_sessions user_sessions_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_token_hash_key UNIQUE (token_hash);


--
-- TOC entry 4985 (class 2606 OID 16563)
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- TOC entry 4987 (class 2606 OID 16559)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4989 (class 2606 OID 16561)
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- TOC entry 4964 (class 1259 OID 16801)
-- Name: idx_account_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_account_user_id ON public.account USING btree (user_id);


--
-- TOC entry 4999 (class 1259 OID 16793)
-- Name: idx_audit_event; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_event ON public.audit_log USING btree (event_type);


--
-- TOC entry 5000 (class 1259 OID 16794)
-- Name: idx_audit_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_time ON public.audit_log USING btree (occurred_at DESC);


--
-- TOC entry 5001 (class 1259 OID 16792)
-- Name: idx_audit_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_audit_user ON public.audit_log USING btree (user_id);


--
-- TOC entry 4971 (class 1259 OID 16490)
-- Name: idx_fee_range; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_fee_range ON public.fee USING btree (amount_min, amount_max);


--
-- TOC entry 5002 (class 1259 OID 16826)
-- Name: idx_otp_challenge_expires_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_otp_challenge_expires_at ON public.otp_challenge USING btree (expires_at);


--
-- TOC entry 5003 (class 1259 OID 16825)
-- Name: idx_otp_challenge_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_otp_challenge_user_id ON public.otp_challenge USING btree (user_id);


--
-- TOC entry 4972 (class 1259 OID 16487)
-- Name: idx_payment_account; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_account ON public.payment USING btree (account_id);


--
-- TOC entry 4973 (class 1259 OID 16489)
-- Name: idx_payment_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_date ON public.payment USING btree (payment_date);


--
-- TOC entry 4974 (class 1259 OID 16488)
-- Name: idx_payment_payee; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_payee ON public.payment USING btree (payee_id);


--
-- TOC entry 4990 (class 1259 OID 16593)
-- Name: idx_sessions_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sessions_active ON public.user_sessions USING btree (is_active);


--
-- TOC entry 4991 (class 1259 OID 16592)
-- Name: idx_sessions_token; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sessions_token ON public.user_sessions USING btree (token_hash);


--
-- TOC entry 4992 (class 1259 OID 16591)
-- Name: idx_sessions_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sessions_user ON public.user_sessions USING btree (user_id);


--
-- TOC entry 4979 (class 1259 OID 16565)
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- TOC entry 4980 (class 1259 OID 16566)
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- TOC entry 4981 (class 1259 OID 16564)
-- Name: idx_users_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_username ON public.users USING btree (username);


--
-- TOC entry 5011 (class 2606 OID 16787)
-- Name: audit_log audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- TOC entry 5006 (class 2606 OID 16796)
-- Name: account fk_account_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- TOC entry 5007 (class 2606 OID 16457)
-- Name: payment payment_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.account(account_id);


--
-- TOC entry 5008 (class 2606 OID 16467)
-- Name: payment payment_fee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_fee_id_fkey FOREIGN KEY (fee_id) REFERENCES public.fee(fee_id);


--
-- TOC entry 5009 (class 2606 OID 16462)
-- Name: payment payment_payee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_payee_id_fkey FOREIGN KEY (payee_id) REFERENCES public.payee(payee_id);


--
-- TOC entry 5010 (class 2606 OID 16586)
-- Name: user_sessions user_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_sessions
    ADD CONSTRAINT user_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


-- Completed on 2026-06-16 14:21:58

--
-- PostgreSQL database dump complete
--

\unrestrict qGLY4esDleEOTC3qvDFXP0ex594UUrjV6EntNhAtCwJbEupph8RxXTeCN9CDocY

