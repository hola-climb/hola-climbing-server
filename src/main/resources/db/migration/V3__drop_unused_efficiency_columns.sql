ALTER TABLE analysis_results
    DROP COLUMN IF EXISTS e_trajectory,
    DROP COLUMN IF EXISTS e_arm,
    DROP COLUMN IF EXISTS raw_data;

ALTER TABLE user_stats
    DROP COLUMN IF EXISTS avg_e_trajectory,
    DROP COLUMN IF EXISTS avg_e_arm;
