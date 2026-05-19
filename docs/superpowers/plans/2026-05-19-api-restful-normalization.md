# API RESTful Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace verb-style project APIs with RESTful, versioned `/api/v1` resource APIs and document the API design rules for course review.

**Architecture:** Keep the current Spring Boot controllers and React single-page frontend, but change request mappings and frontend call sites to the approved resource-oriented URI table. Improve request logging so the monitor records method, URL, status, duration, input, and output summary required by the assignment.

**Tech Stack:** Java 17, Spring Boot 3.4.0, Spring MVC, JPA, React, Vite, Markdown.

---

### Task 1: Backend RESTful Route Replacement

**Files:**
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\controller\AuthController.java`
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\controller\VideoController.java`
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\controller\AdminController.java`
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\config\WebConfig.java`

- [ ] **Step 1: Change controller base paths**

Use these class-level mappings:

```java
@RequestMapping("/api/v1/auth")      // AuthController
@RequestMapping("/api/v1")           // VideoController
@RequestMapping("/api/v1/admin")     // AdminController
```

- [ ] **Step 2: Replace method-level mappings**

Use these mappings exactly:

```java
@PostMapping("/register")            // register
@PostMapping("/login")               // login
@DeleteMapping("/users/me")          // deleteAccount
@GetMapping("/videos/recommendations")
@PostMapping("/videos/{id}/views")
@DeleteMapping("/users/me/views")
@PutMapping("/videos/{id}/like")
@PostMapping("/videos")
@GetMapping("/users/me/videos")
@DeleteMapping("/videos/{id}")
@GetMapping("/request-logs")         // admin logs
@GetMapping("/stats")                // admin stats
```

- [ ] **Step 3: Update JWT exclusions**

In `WebConfig`, protect `/api/v1/**` and exclude only:

```java
.excludePathPatterns("/api/v1/auth/register", "/api/v1/auth/login")
```

- [ ] **Step 4: Verify compilation**

Run: `mvn test`

Expected: Maven build succeeds.

### Task 2: Request Log Compliance

**Files:**
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\config\RequestLoggerFilter.java`
- Modify: `E:\api课程\api-douyin-backend\src\main\java\com\douyin\api\controller\AdminController.java`

- [ ] **Step 1: Extend log model**

Add request body and response summary fields to `RequestLog` with getters.

- [ ] **Step 2: Capture request input and output summary**

Use Spring `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper` inside the filter, call `copyBodyToResponse()` in `finally`, and cap logged body text to 1000 characters.

- [ ] **Step 3: Expose input and output in admin log API**

Add `requestBody` and `responseBody` to each log map returned by `/api/v1/admin/request-logs`.

- [ ] **Step 4: Verify compilation**

Run: `mvn test`

Expected: Maven build succeeds.

### Task 3: Frontend API Call Synchronization

**Files:**
- Modify: `E:\api课程\api-douyin-frontend\src\App.jsx`

- [ ] **Step 1: Add API prefix constant**

Add:

```js
const API_PREFIX = '/api/v1';
```

- [ ] **Step 2: Replace endpoints**

Use these frontend calls:

```js
`${API_PREFIX}/auth/register`
`${API_PREFIX}/auth/login`
`${API_PREFIX}/users/me`
`${API_PREFIX}/videos/recommendations`
`${API_PREFIX}/videos/${videoId}/views`
`${API_PREFIX}/videos/${videoId}/like`
`${API_PREFIX}/users/me/views`
`${API_PREFIX}/admin/stats`
`${API_PREFIX}/admin/request-logs`
`${API_PREFIX}/users/me/videos?page=${page}&limit=6`
`${API_PREFIX}/videos`
`${API_PREFIX}/videos/${videoId}`
```

- [ ] **Step 3: Use correct HTTP methods**

Use `DELETE` for account deletion and view reset, `PUT` for like toggling, and `POST` for publishing and creating view records.

- [ ] **Step 4: Verify frontend build**

Run: `npm run build`

Expected: Vite build succeeds.

### Task 4: API Design Guide

**Files:**
- Create: `E:\api课程\api-douyin-backend\API_DESIGN_GUIDE.md`

- [ ] **Step 1: Document course-aligned principles**

Include REST resource naming, HTTP methods, status codes, JWT stateless auth, cache guidance, versioning, logs, and a final endpoint table.

- [ ] **Step 2: Document rejected old routes**

Explicitly state old verb-style routes such as `/api/videos/publish` and `/api/users/delete` are not allowed in new code.

- [ ] **Step 3: Verify document completeness**

Check no placeholders remain and the endpoint table matches implemented code.

### Task 5: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run backend tests**

Run: `mvn test`

Expected: Build success.

- [ ] **Step 2: Run frontend build**

Run: `npm run build`

Expected: Build success.

- [ ] **Step 3: Review changed files**

Run: `git diff -- src/main/java/com/douyin/api src/main/resources README.md API_DESIGN_GUIDE.md` in backend and `git diff -- src/App.jsx` in frontend.

Expected: Only RESTful route, logging, frontend endpoint, and API guide changes are present.

## Self-Review

Spec coverage: Route replacement, frontend synchronization, logging input/output, versioned URI, and API guide are covered.

Placeholder scan: No TBD/TODO placeholders are present.

Type consistency: Paths use `/api/v1` consistently across backend, frontend, and documentation.
