-- Gym 통합 테스트 시드 데이터.
-- gym 5는 status='closed' — 조회 결과에서 제외되어야 한다.
INSERT INTO gyms (id, name, address, lat, lng, region_code, rating_avg, rating_count, status) VALUES
(1, 'TheClimb Gangnam',     'Seoul Gangnam-gu',  37.4979, 127.0276, 'seoul',    4.50, 120, 'active'),
(2, 'ClimbingPark Hongdae', 'Seoul Mapo-gu',     37.5563, 126.9220, 'seoul',    4.20,  80, 'active'),
(3, 'BoulderProject Pangyo','Gyeonggi Seongnam', 37.3947, 127.1112, 'gyeonggi', 4.70,  60, 'active'),
(4, 'TheClimb Bundang',     'Gyeonggi Seongnam', 37.3825, 127.1189, 'gyeonggi', 4.30,  45, 'active'),
(5, 'ClimbZone Busan',      'Busan Haeundae',    35.1796, 129.0756, 'busan',    4.00,  30, 'closed');

-- 명시적 id로 INSERT했으므로 BIGSERIAL 시퀀스를 최댓값 뒤로 당겨 이후 INSERT 충돌을 막는다.
SELECT setval('gyms_id_seq', (SELECT MAX(id) FROM gyms));

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

INSERT INTO gym_photos (gym_id, gcs_path, caption, display_order) VALUES
(1, 'gyms/1/photo-a.jpg', 'main wall',   0),
(1, 'gyms/1/photo-b.jpg', 'boulder zone', 1);
