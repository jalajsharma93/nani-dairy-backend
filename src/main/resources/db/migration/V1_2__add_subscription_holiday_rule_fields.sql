ALTER TABLE IF EXISTS customer_record
    ADD COLUMN IF NOT EXISTS subscription_holiday_weekdays_csv VARCHAR(140);
