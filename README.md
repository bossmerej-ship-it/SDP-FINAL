# Restaurant Ordering System

Учебный проект по Software Design Patterns. Java 17+, шесть паттернов,
демонстрация полного заказа и входные/выходные модели для будущего веб-интерфейса.

## Запуск

Откройте эту папку в IntelliJ IDEA, выберите JDK 17+, отметьте
src/main/java как Sources Root и src/test/java как Test Sources Root. Запустите `restaurant.Demo.main()`.
Нужен JDK 17 или новее. В macOS/Linux можно использовать:

```sh
sh run.sh
sh test.sh
```

В Windows используйте запуск через IntelliJ IDEA с указанными выше корнями исходников.

Для тестов в IDE запустите `restaurant.RestaurantTests.main()`.
Тесты используют стандартную Java и проверяют расчёты, валидацию, статусы,
события и одновременные запросы. Предупреждение `Test listener failure` ожидаемо:
один тест намеренно подключает неисправного подписчика. Ещё одно ожидаемое
предупреждение проверяет запрет повторного изменения заказа внутри callback.

## Где находятся паттерны

| Паттерн | Основные классы | Наблюдаемый результат |
|---|---|---|
| Factory Method | MealCreator, BurgerCreator, PizzaCreator | По коду блюда создаётся Burger или Pizza |
| Decorator | MealDecorator, Cheese, Sauce | Добавки меняют описание и стоимость Meal |
| Builder | Order.Builder | Заказ собирается по шагам и проходит проверку |
| Strategy | DiscountStrategy, RegularPrice, PercentageDiscount, PriceCalculator | Один заказ стоит 3000.00 или 2700.00 KZT |
| Facade | RestaurantFacade | placeOrder объединяет создание, расчёт и регистрацию |
| Observer | OrderService, OrderObserver, KitchenObserver, CustomerObserver | Подписчики получают события изменения заказа |

`api/` содержит Java records — компактные классы для передачи данных.
`meal/`, `order/`, `pricing/` отвечают за бизнес-правила.
`service/` хранит заказы и управляет статусами.
`Demo` выводит результат. Внутри предметных классов нет Scanner, HTTP или React.

## Поведение

- Burger — 2500.00 KZT, Pizza — 3500.00 KZT.
- Cheese — +300.00 KZT, Sauce — +200.00 KZT за каждую порцию блюда.
- `regular` — обычная цена, `student` — скидка 10% на весь заказ.
- Несколько одинаковых добавок означают несколько порций добавки.
- Количество каждой позиции должно быть положительным.
- TAKEAWAY не содержит номер стола. Для DINE_IN нужен положительный номер.
- Статусы меняются NEW → PREPARING → READY. Повтор текущего статуса допустим.
- Деньги считаются через BigDecimal. Скидка округляется до 2 знаков HALF_UP.
- Заказы хранятся в памяти одного RestaurantFacade до завершения процесса.
- Каждый placeOrder создаёт новый заказ. Идемпотентность HTTP-запросов
  при будущей интеграции следует реализовать в API.
- Observer работает синхронно. Подписчики должны быстро обрабатывать событие
  и не вызывать повторное изменение заказа из своего callback.

## Материалы

- [Объяснение для защиты](docs/DEFENSE_RU.md)
- [Подключение веб-интерфейса](docs/WEB_INTEGRATION.md)
- [PDF-отчёт](docs/report.pdf)
- [UML-исходники и изображения](docs/uml/)
- [Фактический вывод демонстрации](docs/demo-output.txt)

Распределение в отчёте является предложением: Timur Naumov — Factory Method и
Decorator, Madi Talgat — Builder и Strategy, Merey Kuatbay — Facade и Observer.
Перед сдачей внесите группу и подтвердите фактический вклад участников.
Требование самостоятельного вклада каждого участника должно быть выполнено
самой командой согласно правилам курса.

## Обновление отчёта

Текст отчёта и построение SVG-диаграмм находятся в tools/build_report.py.
Для генерации PDF нужен Python с пакетом reportlab.
После изменения кода сначала обновите выводы, затем создайте отчёт:

```sh
sh run.sh > docs/demo-output.txt
sh test.sh > docs/test-output.txt 2>&1
python3 tools/build_report.py
```

Снимок docs/screenshots/demo-output.png показывает записанный вывод
демонстрации в локальном HTML-просмотрщике. После изменений сценария обновите
HTML и снимок либо вставьте собственный снимок запуска из IDE.
