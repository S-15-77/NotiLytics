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



## ⚙️ Installation & Setup

###  Prerequisites
Ensure you have the following installed:

| Tool | Minimum Version | Check Command |
|------|------------------|----------------|
| Java JDK | 8 or 11 | `java -version` |
| sbt | 1.9 + | `sbt sbtVersion` |
| Git | any recent | `git --version` |

### Clone the Repository
```bash
git clone https://github.com/S-15-77/NotiLytics.git
cd NotiLytics
```

###  Configure the API Key
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


