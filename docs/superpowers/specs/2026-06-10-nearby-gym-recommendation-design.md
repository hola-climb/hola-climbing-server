# Nearby Gym Recommendation Design

## Goal

Add a personalized nearby gym recommendation API for authenticated users. The API recommends active gyms around the user's current coordinates by combining location filtering with the user's climbing style embedding.

This design intentionally excludes similar problem or route recommendation. That feature requires a separate problem-level data model or embedding pipeline and remains deferred.

## Endpoint

`GET /api/recommendations/gyms`

Query parameters:

| name | type | required | default | validation |
|---|---:|---:|---:|---|
| `lat` | double | yes | - | `-90..90` |
| `lng` | double | yes | - | `-180..180` |
| `radius` | double | no | `10` | positive kilometers |
| `size` | int | no | `20` | `1..100` |

Authentication is required because the ranking uses the current user's `users.style_embedding`.

## Ranking

Candidate gyms are filtered first:

- `gyms.status = 'active'`
- `gyms.deleted_at IS NULL`
- `gyms.lat` and `gyms.lng` are present
- distance from request coordinates is within `radius` kilometers

Ranking then works in two tiers:

1. If both `users.style_embedding` and `gyms.style_embedding` exist, sort by pgvector cosine distance ascending.
2. If either embedding is missing, use distance fallback.

Final order:

1. gyms with `rankingDistance` first
2. `rankingDistance ASC`
3. `distanceKm ASC`
4. `ratingAvg DESC`
5. `ratingCount DESC`
6. `id ASC`

## Response

Use a new response DTO instead of extending `GymSummaryResponse`, because the recommendation API needs ranking metadata.

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "TheClimb Gangnam",
      "address": "서울 강남구 ...",
      "thumbnailUrl": null,
      "regionCode": "SEOUL",
      "ratingAvg": 4.5,
      "ratingCount": 12,
      "distanceKm": 2.31,
      "rankingDistance": 0.18,
      "source": "style_match"
    }
  ]
}
```

Fields:

- `distanceKm`: distance from the requested coordinates, rounded by JSON serialization rules without server-side string formatting.
- `rankingDistance`: pgvector cosine distance when available; otherwise `null`.
- `source`: `style_match` when style ranking is available, otherwise `nearby`.

## Architecture

Add the feature inside the existing recommendation domain:

- `RecommendationController`: add `GET /api/recommendations/gyms`.
- `RecommendationService`: add `getNearbyGyms(userId, lat, lng, radius, size)`.
- `RecommendationServiceImpl`: validate coordinates and delegate to mapper.
- `RecommendationMapper`: add `findNearbyGyms`.
- `RecommendationMapper.xml`: implement SQL with a viewer CTE, distance calculation, vector distance, and fallback ordering.
- `RecommendedGymResponse`: new DTO in `domain/recommendation/dto/response`.

Do not move existing `/api/gyms/nearby`. It remains the public distance-only API.

## Error Handling

- Missing or invalid token: existing security returns `C002`.
- Non-positive `radius` or `size`: validation returns `C001`.
- `lat` outside `-90..90`: `C001`.
- `lng` outside `-180..180`: `C001`.

## Testing

Add tests to `RecommendationIntegrationTest`:

1. Authenticated request with user and gym embeddings returns nearby gyms ordered by style similarity.
2. When user embedding is missing, the same endpoint falls back to distance order and returns `source = nearby`.
3. Gyms outside the radius are excluded.
4. Invalid latitude or longitude returns `400`.
5. Missing token returns `401`.

The tests should reuse existing pgvector Testcontainers setup and deterministic `style_embedding` helper methods already present in the recommendation tests.

## API Documentation Follow-up

After implementation, add Notion API documentation:

- 기능: `암장 추천`
- HTTP 메서드: `GET`
- API Path: `/api/recommendations/gyms`
- 분류: `추천`, `암장`
- 백: `Done`
- 프론트: `Not started`

## Rollback Cost

Low. The change adds a new endpoint, mapper query, DTO, and tests without changing existing public APIs. Reverting the feature removes only the new recommendation gym path and related tests.
