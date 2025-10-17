# 📰 NotiLytics
*A reactive news analytics web application built with the Play Framework (Java)*

---

## 📘 Overview
**NotiLytics** is developed as part of the **SOEN 6441 – Software Project Management (Fall 2025)** course at **Concordia University**.  
It demonstrates Java 8 functional programming, asynchronous web development with **Play Framework**, and reactive data analysis using **Streams** and **Futures**.

The goal is to build a system that retrieves and analyzes live news feeds from the **[News API](https://newsapi.org)**.  
The app allows users to:
- Search for recent articles by keyword or phrase
- View the 10 most recent results (title, source, and publication date)
- Extend functionality with additional analytics such as:
    - 🔤 Word frequency statistics
    - 😊 Sentiment classification (happy / sad / neutral)
    - 📚 Readability scoring (Flesch-Kincaid)
    - 🌐 Source profile pages

---

## 🧩 Tech Stack
| Component | Technology |
|------------|-------------|
| Language | Java 8 + |
| Framework | Play Framework 3.x |
| Build Tool | sbt 1.9 + |
| View Engine | Scala HTML templates |
| Testing | JUnit 5 + Mockito |
| Code Coverage | JaCoCo |
| API | [News API v2](https://newsapi.org/docs/endpoints/everything) |

---

## ⚙️ Installation & Setup

### 1️⃣ Prerequisites
Ensure you have the following installed:

| Tool | Minimum Version | Check Command |
|------|------------------|----------------|
| Java JDK | 8 or 11 | `java -version` |
| sbt | 1.9 + | `sbt sbtVersion` |
| Git | any recent | `git --version` |

### 2️⃣ Clone the Repository
```bash
git clone https://github.com/S-15-77/NotiLytics.git
cd NotiLytics
```

### 3️⃣ Configure the API Key
Create a free account at [newsapi.org/register](https://newsapi.org/register) and get your API key.  
Then open **`conf/application.conf`** and add:
```conf
newsapi.key="YOUR_API_KEY"
newsapi.url="https://newsapi.org/v2/everything"
```

### 4️⃣ Run the Application
```bash
sbt run
```
Open your browser at **[http://localhost:9000](http://localhost:9000)**.  
You should see:
> *“Welcome to NotiLytics – Play Framework setup successful.”*

### 5️⃣ Run Tests
```bash
sbt test
```

---

## 🧠 Project Structure
```
app/
 ├── controllers/        → Handles HTTP requests (HomeController.java, etc.)
 ├── models/             → Data models and business logic
 ├── services/           → API & processing logic (e.g., NewsService.java)
 └── views/              → UI templates (Scala HTML)
conf/
 └── routes              → URL-to-controller mappings
public/                  → Static assets (CSS, JS, images)
test/                    → JUnit & Mockito test classes
build.sbt                → Build configuration
```

---

## 🧪 Coding Guidelines
- Follow **MVC** separation (no business logic in controllers).
- Use **`CompletableFuture`** for async operations — no blocking (`.get()` / `.join()`) in main code.
- All data processing must use **Java Streams API**.
- Every public and private method should include **Javadoc** comments.
- Achieve **100 % code coverage** (classes, methods, and lines) using **JaCoCo**.
- Use mock objects (e.g., Mockito or DI) instead of calling the live API in tests.

---

## 🧑‍🤝‍🧑 Team Members
| Name | Student ID | Role / Feature |
|------|-------------|----------------|
| **Santhosh Dayakar** | 40302539 | Project Setup + Controller Integration |
| Member 2 | … | Word Stats |
| Member 3 | … | Sentiment Analysis |
| Member 4 | … | Readability |
| Member 5 | … | Source Profile |

---

## 🌿 Branching & Contribution Guide
1. **Clone the repo**
   ```bash
   git clone https://github.com/S-15-77/NotiLytics.git
   cd NotiLytics
   ```

2. **Create a new branch** for your feature:
   ```bash
   git checkout -b feature/<your-feature-name>
   ```

3. **Commit changes**
   ```bash
   git add .
   git commit -m "Implement <your-feature-name>"
   ```

4. **Push and create a Pull Request**
   ```bash
   git push origin feature/<your-feature-name>
   ```
    - Go to GitHub → create a **Pull Request**.
    - Get at least one team review before merging into **main**.

---

## 📜 License
This project is for academic use under the **Concordia University SOEN 6441** guidelines.  
All rights reserved © 2025 NotiLytics Team.

