-- ===========================================================================
-- V4 — repair image download URLs.
--
-- Images uploaded through POST /images were stored with the URL
-- /api/v1/images/image/download/{id}, a path no endpoint serves. The download
-- endpoint is GET /api/v1/images/{id}; rewrite the stored value to match (the
-- code now writes the correct URL). Seeded images already used the right path,
-- so only rows with the old prefix are touched.
-- ===========================================================================

update image
set url = concat('/api/v1/images/', id)
where url like '/api/v1/images/image/download/%';
