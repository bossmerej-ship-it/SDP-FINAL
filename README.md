# Restaurant Ordering System

A Java 17 application for ordering burgers and pizza. Customers can customize meals, place takeaway or dine-in orders, and track their progress. The kitchen board moves orders from New to Preparing to Ready.

## Run locally

Install JDK 17 or later. Run the appropriate command from the project root.

| Platform | Command |
| --- | --- |
| macOS or Linux | `sh ./run-web.sh` |
| Windows PowerShell | `.\run-web.ps1` |

Open [http://127.0.0.1:8080](http://127.0.0.1:8080). If the port is busy, run `sh ./run-web.sh 8081` or `.\run-web.ps1 -Port 8081` and open port 8081 instead.

The server runs on localhost. Orders and browser sessions are stored in memory and reset when the server stops. The kitchen board has no separate sign-in.

## Project structure

- `web/` contains the menu, cart, order tracking, and kitchen board.
- `src/main/java/restaurant/` contains the order model, business rules, and HTTP server.
- `src/test/java/restaurant/` contains the Java and HTTP tests.

| Pattern | Role |
| --- | --- |
| Factory Method | `MealCreator` and its subclasses create burgers and pizzas. |
| Decorator | `Cheese` and `Sauce` add their price and description to a meal. |
| Builder | `Order.Builder` validates and assembles the order details. |
| Strategy | `PriceCalculator` uses a `DiscountStrategy` to calculate regular or discounted totals. |
| Facade | `RestaurantFacade` coordinates menu, pricing, and order operations. |
| Observer | `OrderService` notifies registered observers when an order is placed or changes status. |

## Run tests

Use `sh ./test.sh` on macOS or Linux, or `.\test.ps1` in Windows PowerShell. If Node.js is installed, run `node --test tools/submission.test.mjs` for the browser submission retry test.

Run `sh ./run.sh` on macOS or Linux to try the console version. In an IDE, run `restaurant.Demo.main()` with `src/main/java` as the source root.
