-- Terms 도메인 통합 테스트 시드. users-schema.sql 다음에 실행.
-- service/privacy는 필수, marketing은 선택.
INSERT INTO terms_versions (id, type, version, title, content, is_required, effective_at) VALUES
(1, 'service',   '1.0', '서비스 이용약관',      '서비스 이용약관 본문',   TRUE,  '2026-01-01 00:00:00'),
(2, 'privacy',   '1.0', '개인정보 처리방침',    '개인정보 처리방침 본문', TRUE,  '2026-01-01 00:00:00'),
(3, 'marketing', '1.0', '마케팅 정보 수신 동의', '마케팅 수신 동의 본문',  FALSE, '2026-01-01 00:00:00');
