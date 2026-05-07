INSERT INTO trains (code, origin, destination, departure, arrival, price, available_seats, active)
SELECT 'SE1', 'Hanoi', 'Ho Chi Minh', '06:00', '18:45', 980000, 120, TRUE
WHERE NOT EXISTS (SELECT 1 FROM trains WHERE code = 'SE1');

INSERT INTO trains (code, origin, destination, departure, arrival, price, available_seats, active)
SELECT 'SE3', 'Hanoi', 'Da Nang', '08:30', '18:10', 680000, 88, TRUE
WHERE NOT EXISTS (SELECT 1 FROM trains WHERE code = 'SE3');

INSERT INTO trains (code, origin, destination, departure, arrival, price, available_seats, active)
SELECT 'SE5', 'Ho Chi Minh', 'Da Nang', '07:15', '19:05', 720000, 94, TRUE
WHERE NOT EXISTS (SELECT 1 FROM trains WHERE code = 'SE5');

INSERT INTO trains (code, origin, destination, departure, arrival, price, available_seats, active)
SELECT 'SE7', 'Hai Phong', 'Hanoi', '09:00', '11:10', 180000, 200, TRUE
WHERE NOT EXISTS (SELECT 1 FROM trains WHERE code = 'SE7');

INSERT INTO trains (code, origin, destination, departure, arrival, price, available_seats, active)
SELECT 'SE9', 'Da Nang', 'Ho Chi Minh', '14:20', '02:15', 760000, 76, TRUE
WHERE NOT EXISTS (SELECT 1 FROM trains WHERE code = 'SE9');