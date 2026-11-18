-- Generate a bcrypt hash for your benchmark password with:
--   htpasswd -bnBC 10 "" <password> | tr -d ':\n'
-- and replace <BCRYPT_HASH> before running.
INSERT INTO players (id, username, email, password_hash, role, status, rating,
                     status_changed_at, created_at, updated_at, version)
SELECT gen_random_uuid(),
       'bench_' || g,
       'bench_' || g || '@example.test',
       '<BCRYPT_HASH>',
       'PLAYER', 'ACTIVE',
       500 + floor(random() * 2000)::int,
       now(), now(), now(), 0
FROM generate_series(1, 1000000) AS g;
ANALYZE players;
