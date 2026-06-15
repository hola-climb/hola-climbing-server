UPDATE gyms g
SET thumbnail_url = first_photo.gcs_path
FROM (
    SELECT DISTINCT ON (gym_id)
           gym_id,
           gcs_path
    FROM gym_photos
    ORDER BY gym_id, display_order, id
) first_photo
WHERE g.id = first_photo.gym_id
  AND (g.thumbnail_url IS NULL OR g.thumbnail_url = '');

DROP TABLE IF EXISTS gym_photos;
