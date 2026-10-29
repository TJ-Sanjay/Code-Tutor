# CodeTutor

Understand any **Spring Boot, Django (+ Django REST Framework) or React** codebase in minutes. Paste a GitHub link or upload a ZIP —
CodeTutor reads the project, draws how it works and answers questions about it, with the exact lines highlighted.

---

## 1. Quick start

### Prerequisites

| Tool | Version | Check with |
|---|---|---|
| Java (JDK) | 21 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 18 or newer (with npm) | `node -v` |
| MySQL | 8.0 (service running on port 3306) | `mysql --version` |
| Internet | needed to clone GitHub repos and load fonts | – |

No Docker, Redis or API key is required. The database is **MySQL**: run **`setup-mysql.cmd`** once (double-click; it asks for your MySQL root password and creates the `codetutor` database and user).

### Start everything (Windows)

Double-click **`start-dev.cmd`** in the project root. It opens two windows (backend and frontend; the first run also does `npm install`).
After about 20–30 seconds open **http://localhost:5173**.

### Start manually (any OS)

```bash
# Terminal 1 – backend API on http://localhost:8080/api
cd backend
mvn spring-boot:run

# Terminal 2 – web app on http://localhost:5173
cd frontend
npm install        # first time only
npm run dev
```

### First use

1. Open http://localhost:5173 and choose **Create account** (every account has its own private workspace).
2. On the dashboard paste a GitHub URL — for example `https://github.com/spring-projects/spring-petclinic` — and press **Analyse**,
   or switch to **Upload ZIP** and drop a zip of your project.
3. Wait for the progress bar to finish (a few seconds to a minute), then click the project card.
4. Explore the tabs: **Overview · Code · Architecture · Data flow · Database · APIs · Dependencies**, and open **Ask AI** for questions.

Stop the servers by closing their windows (or Ctrl+C in each terminal).

---

## 2. What you get

| Feature | Where |
|---|---|
| Private accounts – register / sign in; other users get *404* for anything that is not theirs | Sign-in page |
| Import a GitHub URL or ZIP with size / file-count limits, safe extraction; `node_modules`, `target`, `.git`… skipped; secret files (`.env`, keys) **never read unless you allow it** | Dashboard |
| Tech stack with language percentages, key numbers, layers, modules, security summary | *Overview* |
| Read-only code explorer with IDE-style colours; **click any file, class, method or field for a short explanation popup** | *Code* |
| Layered architecture diagram (+ module view, filters, PNG export) | *Architecture* |
| Data-flow diagrams per endpoint, "how security works", "login flow" | *Data flow* |
| ER / database diagram from JPA entities and SQL scripts (PNG export) | *Database* |
| API catalogue with parameters, return types and required access | *APIs* |
| Interactive dependency tree + Maven / npm libraries | *Dependencies* |
| Questions answered from the code; relevant file opens with lines highlighted; flows drawn inline; history saved per user | *Ask AI* panel |

Press **Ctrl + K** anywhere in a project to search classes, methods, endpoints and files.

---

## 3. Configuration (all optional)

Settings live in `backend/src/main/resources/application.yml`; the ones below can be set as environment variables **before** starting the backend
(Windows: `set NAME=value`, macOS/Linux: `export NAME=value`).

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | Secret used to sign login tokens. **Set your own long random value before sharing or deploying.** | built-in dev value |
| `LLM_PROVIDER` | `anthropic`, `openai`, `gemini` or `none` | `none` |
| `LLM_API_KEY` | API key for the provider above | – |
| `LLM_MODEL` | Model name (optional) | provider default |
| `DB_PASSWORD` | Password of the MySQL application user | `codetutor` |

Limits (max files, repo size, projects per user…) are under `limits:` in `application.yml`.

### Do I need an API key?

**No.** Everything works without one. The chat then answers from the project's knowledge graph and semantic index (always grounded in the real code).
Add `LLM_PROVIDER` + `LLM_API_KEY` only if you want an LLM to phrase the answers more naturally; it still only sees retrieved code and graph facts.
The chat header shows *"Powered by …"* when a key is active.

### Data and database

Accounts, projects and chats are stored in **MySQL** (database `codetutor`, user `codetutor` / password `codetutor` — change them with the `DB_USER`, `DB_PASSWORD` or `DB_URL` environment variables). Cloned repositories and analysis files live in `backend/data/`.
If an old H2 file (`backend/data/db/codetutor.mv.db`) exists, its data is copied into MySQL automatically on the first start.

---

## 4. Troubleshooting

| Problem | Fix |
|---|---|
| Page says it *cannot reach the server* | The backend is not running — start it (`mvn spring-boot:run`) and wait until it prints `Started CodeTutorApplication`. |
| `Port 8080 / 5173 already in use` | Close the old window / process using that port, or change `server.port` (backend) / `server.port` in `frontend/vite.config.js`. |
| A project shows **Failed** | Hover/read the message on the card. Typical causes: private or misspelt GitHub URL, repository larger than the limits, corrupt ZIP. Press **Try again** or delete it. |
| Signed out unexpectedly | Tokens last 24 h. Sign in again. |
| `mvn` or `node` not found | Install the tools from the prerequisites table and reopen the terminal. |
| Want to reset everything | Stop the backend and delete the `backend/data` folder. |

---

## 5. How it works

```
GitHub URL / ZIP ─► validate ─► clone / extract (limits, skip junk) ─► scan files
   ─► tech stack (pom / package.json / Docker …)   ─► JavaParser AST (classes, methods, resolved calls)
   ─► layers & modules ─► endpoints ─► security rules ─► DB schema (JPA + SQL, Django models) ─► React files & API calls
   ─► knowledge graph (nodes + typed edges) ─► semantic chunks (module.layer.class.method) ─► vector index
```

* `backend/src/main/java/com/codetutor/backend/`
  `analysis/` analyzers, knowledge graph, chunks, storage · `flow/` data-flow generator · `explain/` popup explanations and code colouring ·
  `chat/` Q&A engine · `project/` async import pipeline and per-user access control · `auth/`, `security/` accounts and JWT
* `frontend/src/` — `pages/` (sign-in, dashboard, workspace) and `views/` (one file per tab)

### Design notes and limits

* Java is parsed with **JavaParser**; the React side with regex-based analysis. **Embeddings are local TF-IDF vectors** (identifier splitting + synonyms) —
  no external service. The index is behind one class (`VectorIndex`), so neural embeddings / pgvector can be swapped in.
* Background work uses an in-process thread pool (not Redis), with progress polling.
* Deep analysis targets **Spring Boot**, **Django / Django REST Framework** and **React** projects; other files are listed and viewable but not graphed.

### Django support (`analysis/python/`)

Python is read with a small indentation-aware parser (`PyParser`) — no Python install is needed. `PythonAnalyzer` + `DjangoSupport` produce the
same model the Java analysis does, so every tab (architecture, data flow, database, APIs, dependencies, code explorer, chat) works unchanged:

| Django concept | What CodeTutor builds from it |
| --- | --- |
| `models.py` (`models.Model`, abstract bases, `AbstractUser`) | Tables, columns, primary/foreign keys, one-to-one and many-to-many join tables → the ER diagram |
| `urls.py` (`path`, `re_path`, `include`, DRF `router.register`) | The real endpoint list with full URL paths, path parameters and HTTP methods |
| Function views, class-based views, generic views, DRF `ViewSet` / `@action` | Controllers and their handlers (inherited handlers are shown as built-in steps) |
| `@login_required`, `LoginRequiredMixin`, `permission_classes`, `REST_FRAMEWORK` defaults | Per-endpoint access level (public / login / role / permission) |
| `settings.py` (`MIDDLEWARE`, `AUTHENTICATION`, CSRF, CORS, `DEBUG`, `SECRET_KEY`) | The security flow: ordered middleware chain, mechanisms, and warnings |
| `Model.objects.create(...)`, custom managers / querysets, services | Call graph and data flow down to the database table touched |

Flask and FastAPI routes (`@app.route`, `@router.get`) are recognised too. A sample project lives in `backend/src/test/resources/django-sample`.

## 6. Tests

```bash
cd backend && mvn test
```

Covers the analyzers (layers, calls, endpoints, schema, security rules) and the privacy suite that proves one user can never read another user's projects.
