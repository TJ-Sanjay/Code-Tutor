-- Insert test clubs
INSERT INTO clubs (name, description, category, status, contact_email, created_at, updated_at)
VALUES 
('Tech Club', 'A club for technology enthusiasts to learn and build projects', 'TECHNICAL', 'ACTIVE', 'tech@college.com', NOW(), NOW()),
('Cultural Club', 'Celebrating diversity through cultural events and activities', 'CULTURAL', 'ACTIVE', 'cultural@college.com', NOW(), NOW()),
('Sports Club', 'Promoting fitness and sportsmanship through various sports', 'SPORTS', 'ACTIVE', 'sports@college.com', NOW(), NOW()),
('Music Club', 'For music lovers to learn, perform and appreciate music', 'MUSIC', 'ACTIVE', 'music@college.com', NOW(), NOW()),
('Dance Club', 'Express yourself through various dance forms', 'DANCE', 'ACTIVE', 'dance@college.com', NOW(), NOW());

-- Insert test events
INSERT INTO events (name, description, club_id, date_time, venue, entry_fee, max_participants, registration_deadline, contact_email, active, created_at, updated_at)
VALUES
('Hackathon 2025', '24-hour coding competition', 1, '2025-01-15 09:00:00', 'Computer Lab', 100.00, 50, '2025-01-10 23:59:59', 'tech@college.com', true, NOW(), NOW()),
('Cultural Fest', 'Annual cultural celebration', 2, '2025-01-20 10:00:00', 'Main Auditorium', 0.00, 200, '2025-01-18 23:59:59', 'cultural@college.com', true, NOW(), NOW()),
('Football Tournament', 'Inter-college football championship', 3, '2025-01-25 08:00:00', 'Sports Ground', 50.00, 100, '2025-01-22 23:59:59', 'sports@college.com', true, NOW(), NOW()),
('Music Concert', 'Live music performance by students', 4, '2025-02-01 18:00:00', 'Amphitheater', 0.00, 150, '2025-01-30 23:59:59', 'music@college.com', true, NOW(), NOW());

-- Verify data
SELECT * FROM clubs;
SELECT * FROM events;
