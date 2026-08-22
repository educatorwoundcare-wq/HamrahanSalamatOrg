SELECT lower(regexp_replace('fullName', '([a-z])([A-Z])', '\1_\2', 'g'));
SELECT lower(regexp_replace('referralId', '([a-z])([A-Z])', '\1_\2', 'g'));
SELECT lower(regexp_replace('registrationDate', '([a-z])([A-Z])', '\1_\2', 'g'));
