-- Gym 통합 테스트 시드 데이터.
-- gym 5는 status='closed' — 조회 결과에서 제외되어야 한다.
INSERT INTO gyms (id, name, address, lat, lng, thumbnail_url, region_code, rating_avg, rating_count, status) VALUES
(1, 'TheClimb Gangnam',     'Seoul Gangnam-gu',  37.4979, 127.0276, 'gyms/profile-images/1/seed.jpg', 'seoul',    4.50, 120, 'active'),
(2, 'ClimbingPark Hongdae', 'Seoul Mapo-gu',     37.5563, 126.9220, NULL,                                'seoul',    4.20,  80, 'active'),
(3, 'BoulderProject Pangyo','Gyeonggi Seongnam', 37.3947, 127.1112, NULL,                                'gyeonggi', 4.70,  60, 'active'),
(4, 'TheClimb Bundang',     'Gyeonggi Seongnam', 37.3825, 127.1189, NULL,                                'gyeonggi', 4.30,  45, 'active'),
(5, 'ClimbZone Busan',      'Busan Haeundae',    35.1796, 129.0756, NULL,                                'busan',    4.00,  30, 'closed');

-- 명시적 id로 INSERT했으므로 BIGSERIAL 시퀀스를 최댓값 뒤로 당겨 이후 INSERT 충돌을 막는다.
SELECT setval('gyms_id_seq', (SELECT MAX(id) FROM gyms));

-- 추천 피드 벡터 정렬 검증용 deterministic embedding.
UPDATE gyms
SET style_embedding = ('[' || array_to_string(ARRAY(
    SELECT CASE WHEN n = 1 THEN '1' ELSE '0' END
    FROM generate_series(1, 64) AS gs(n)
), ',') || ']')::vector
WHERE id = 1;

UPDATE gyms
SET style_embedding = ('[' || array_to_string(ARRAY(
    SELECT CASE WHEN n = 2 THEN '1' ELSE '0' END
    FROM generate_series(1, 64) AS gs(n)
), ',') || ']')::vector
WHERE id = 2;

INSERT INTO gym_grades (id, gym_id, label, difficulty_order, is_active) VALUES
(1001, 1, '초록', 10, TRUE),
(1002, 1, '파랑', 20, TRUE),
(1003, 1, '빨강', 30, TRUE),
(1004, 2, '노랑', 10, TRUE),
(1005, 2, '보라', 20, TRUE),
(1006, 2, '회색', 30, FALSE),
(1007, 3, 'V3', 10, TRUE),
(1008, 3, 'V4', 20, TRUE),
(1009, 3, 'V5', 30, TRUE),
(1010, 4, '검정', 10, TRUE)
ON CONFLICT DO NOTHING;

SELECT setval('gym_grades_id_seq', (SELECT MAX(id) FROM gym_grades));

-- gym 1: 요일별 운영시간 시드 (일요일 휴무).
UPDATE gyms SET business_hours = '{
  "mon": {"open": "06:00", "close": "23:00"},
  "tue": {"open": "06:00", "close": "23:00"},
  "wed": {"open": "06:00", "close": "23:00"},
  "thu": {"open": "06:00", "close": "23:00"},
  "fri": {"open": "06:00", "close": "23:00"},
  "sat": {"open": "09:00", "close": "21:00"},
  "sun": null
}'::jsonb WHERE id = 1;
