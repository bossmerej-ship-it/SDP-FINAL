"""Regenerate the report and UML SVGs from the project's current sources.
Requires Python and reportlab. Run from any working directory.
"""
from pathlib import Path
import textwrap
from xml.sax.saxutils import escape
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Image, Preformatted
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.graphics.shapes import Drawing, Rect, Line, String, Polygon
from reportlab.graphics import renderSVG

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
SRC = ROOT / "src/main/java/restaurant"
INK = colors.HexColor("#193e48")
ACCENT = colors.HexColor("#137b77")
PALE = colors.HexColor("#edf5f5")
GRAY = colors.HexColor("#526369")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleCustom", fontName="Helvetica-Bold", fontSize=29, leading=34, textColor=INK, spaceAfter=16))
styles.add(ParagraphStyle(name="SectionCustom", fontName="Helvetica-Bold", fontSize=18, leading=23, textColor=INK, spaceAfter=12))
styles.add(ParagraphStyle(name="TextCustom", fontName="Helvetica", fontSize=10.5, leading=15.5, textColor=INK, spaceAfter=10))
styles.add(ParagraphStyle(name="SmallCustom", fontSize=9, leading=12, textColor=GRAY, spaceAfter=8))
styles.add(ParagraphStyle(name="CodeCustom", fontName="Courier", fontSize=8, leading=10.5, textColor=INK, backColor=PALE, borderPadding=8, spaceAfter=12))
story = []

def p(text, style="TextCustom"):
    story.append(Paragraph(text, styles[style]))

def title(text):
    p(text, "SectionCustom")

def page():
    story.append(PageBreak())

def table(rows, widths):
    cells = [[Paragraph(escape(str(c)), styles["SmallCustom"]) for c in row] for row in rows]
    t = Table(cells, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,0), PALE),
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("LINEBELOW", (0,0), (-1,0), 1, ACCENT),
        ("LINEBELOW", (0,1), (-1,-1), .4, colors.HexColor("#d7e1e3")),
        ("LEFTPADDING", (0,0), (-1,-1), 8),
        ("RIGHTPADDING", (0,0), (-1,-1), 8),
        ("TOPPADDING", (0,0), (-1,-1), 8),
        ("BOTTOMPADDING", (0,0), (-1,-1), 5),
    ]))
    story.extend([t, Spacer(1,12)])

def code(text):
    story.append(Preformatted(textwrap.dedent(text).strip(), styles["CodeCustom"]))

def source(path):
    return (SRC / path).read_text()

def method(path, start, end):
    text = source(path)
    return text[text.index(start):text.index(end)].rstrip()

def box(d,x,y,w,h,name,lines=()):
    d.add(Rect(x,y,w,h,strokeColor=INK,fillColor=colors.white,strokeWidth=.8))
    d.add(Rect(x,y+h-27,w,27,strokeColor=INK,fillColor=PALE,strokeWidth=.8))
    d.add(String(x+w/2,y+h-18,name,fontName="Helvetica-Bold",fontSize=9,textAnchor="middle",fillColor=INK))
    for i,line in enumerate(lines):
        d.add(String(x+8,y+h-43-i*13,line,fontName="Helvetica",fontSize=8,fillColor=INK))

def arrow(d,x1,y1,x2,y2,kind="uses",label=""):
    import math
    d.add(Line(x1,y1,x2,y2,strokeColor=GRAY,strokeWidth=.8,
               strokeDashArray=[4,3] if kind in ("implements","uses") else []))
    angle=math.atan2(y2-y1,x2-x1)
    size=8
    bx=x2-size*math.cos(angle)
    by=y2-size*math.sin(angle)
    points=[x2,y2,bx+4*math.sin(angle),by-4*math.cos(angle),bx-4*math.sin(angle),by+4*math.cos(angle)]
    d.add(Polygon(points,strokeColor=GRAY,fillColor=colors.white if kind in ("extends","implements") else GRAY,strokeWidth=.8))
    if label:
        d.add(String((x1+x2)/2+5,(y1+y2)/2+6,label,fontName="Helvetica",fontSize=7,fillColor=GRAY))

def diagram(name,d):
    renderSVG.drawToFile(d,str(DOCS/"uml"/(name+".svg")))
    story.append(d)
    story.append(Spacer(1,10))

p("SOFTWARE DESIGN PATTERNS", "SmallCustom")
story.append(Spacer(1,35))
p("Restaurant<br/>Ordering System", "TitleCustom")
p("Final project report", "SectionCustom")
p("A Java application demonstrating six coordinated design patterns through menu selection, order construction, pricing and status notifications.")
story.append(Spacer(1,20))
table([
    ["Team member", "Proposed pattern responsibility"],
    ["Timur Naumov", "Factory Method and Decorator"],
    ["Madi Talgat", "Builder and Strategy"],
    ["Merey Kuatbay", "Facade and Observer"],
], [190,305])
p("Group: ____________________<br/>Prepared on 14 November 2026", "SmallCustom")
story.append(Spacer(1,18))
p("Implementation package", "SectionCustom")
p("Java sources, a runnable demonstration, regression checks, UML diagrams, captured application output and a guide to future web integration.")
p("The allocation above is proposed. Each student must independently implement and explain at least two patterns under the course requirements. The team must confirm its actual contributions before submission.", "SmallCustom")
page()

title("1. Introduction and requirements")
p("The project models restaurant ordering. A customer selects a burger or pizza, chooses optional toppings and creates an order for takeaway or dine-in service. The application calculates an exact decimal price, applies the selected discount, stores the order and informs subscribers when its status changes.")
p("The design keeps presentation separate from business behavior. Demo.java drives the application through RestaurantFacade. The facade takes plain request objects and returns immutable response snapshots, providing a practical boundary for a later HTTP controller.")
table([
    ["Requirement", "Implementation evidence"],
    ["At least six distinct course patterns", "Factory Method, Decorator, Builder, Strategy, Facade and Observer"],
    ["One cohesive working project", "Demo.main -> RestaurantFacade -> domain components -> OrderService"],
    ["At least two patterns per student", "Proposed allocation on the cover; individual contribution to be confirmed by the team"],
    ["Java / Go source code", "Java 17-compatible source in src/main/java; complete code supplied in ZIP"],
    ["Report, screenshots and UML", "This PDF, output capture, class diagrams and a sequence diagram"],
    ["Defense", "Runnable demonstration and docs/DEFENSE_RU.md"],
], [180,315])
p("The supplied syllabus specifies submission of the PDF and source ZIP by the end of Week 9 and defense in Week 10. Calendar dates depend on the course schedule.", "SmallCustom")
title("Scope and business rules")
p("The menu has two meals and two toppings. Regular pricing applies no discount; student pricing applies 10%. Dine-in requires a positive table number. Quantities must be positive. A valid order progresses from NEW to PREPARING to READY. Repeating the current status is a no-op.")
p("Orders remain in memory for the lifetime of one facade instance. Each placement creates a new identifier. Monetary values use BigDecimal with two decimal places and HALF_UP rounding for the discount.")
page()

title("2. Factory Method and Decorator")
p("<b>Factory Method (Lecture 2).</b> Meal is the product interface. Burger and Pizza are concrete products. MealCreator declares createMeal(), while BurgerCreator and PizzaCreator override it. The facade looks up a creator by the requested meal code and calls the factory method through the abstract type.")
p("<b>Decorator (Lecture 5).</b> MealDecorator implements Meal and stores another Meal. Cheese and Sauce extend it, delegate description and price queries to the wrapped component, then append their own behavior. The chain Sauce(Cheese(Burger)) costs 2500 + 300 + 200 = 3000 KZT.")
d=Drawing(495,370)
box(d,15,265,165,65,"MealCreator",["+ createMeal(): Meal"])
box(d,5,150,175,60,"BurgerCreator",["+ createMeal(): Meal"])
box(d,5,45,175,60,"PizzaCreator",["+ createMeal(): Meal"])
arrow(d,92,210,92,265,"extends")
d.add(Line(180,75,190,75,strokeColor=GRAY,strokeWidth=.8))
d.add(Line(190,75,190,235,strokeColor=GRAY))
arrow(d,190,235,155,265,"extends")
box(d,305,265,175,80,"<<interface>> Meal",["+ getDescription(): String","+ getPrice(): BigDecimal"])
arrow(d,180,302,305,302,"uses","creates")
box(d,305,145,175,75,"MealDecorator",["# meal: Meal"])
arrow(d,392,220,392,265,"implements")
box(d,270,40,100,55,"Cheese")
box(d,390,40,100,55,"Sauce")
arrow(d,320,95,365,145,"extends")
arrow(d,440,95,420,145,"extends")
d.add(String(245,245,"Burger and Pizza",fontName="Helvetica",fontSize=8,fillColor=GRAY))
d.add(String(245,232,"also implement Meal.",fontName="Helvetica",fontSize=8,fillColor=GRAY))
diagram("factory-decorator",d)
p("UML notation used in the class views: hollow triangles denote inheritance or interface realization. Dashed filled arrows denote dependencies. The wrapped Meal field represents the decorator's reference to its component.", "SmallCustom")
page()

title("3. Builder and Strategy")
p("<b>Builder (Lecture 1).</b> Order.Builder accumulates items, customer details, service type, table number and comment. Fluent methods return the builder. build() rejects incomplete orders and invokes the private Order constructor. List.copyOf captures the selected items, so later builder changes do not alter the completed order.")
p("<b>Strategy (Lecture 7).</b> DiscountStrategy defines discountFor(subtotal). RegularPrice returns zero, while PercentageDiscount calculates a configurable percentage. PriceCalculator is the context. It sums line totals and delegates discount calculation to the supplied strategy. The facade chooses a new context for each request.")
d=Drawing(495,320)
box(d,5,220,220,85,"Order.Builder",["+ addItem(Meal, int): Builder","+ build(): Order"])
box(d,290,220,200,85,"Order",["- items: List<OrderItem>","- serviceType: ServiceType"])
arrow(d,225,262,290,262,"uses","creates")
box(d,5,100,200,70,"PriceCalculator",["- strategy: DiscountStrategy","+ calculate(Order): PriceBreakdown"])
box(d,280,100,210,70,"<<interface>> DiscountStrategy",["+ discountFor(BigDecimal)"])
arrow(d,205,135,280,135,"uses")
box(d,255,0,115,50,"RegularPrice")
box(d,380,0,115,50,"PercentageDiscount")
arrow(d,312,50,345,100,"implements")
arrow(d,437,50,420,100,"implements")
diagram("builder-strategy",d)
table([
    ["Calculation", "Expected result"],
    ["Burger + cheese + sauce", "3000.00 KZT"],
    ["10% discount on 3000.00", "300.00 KZT discount; 2700.00 KZT total"],
    ["Two pizzas with cheese; regular pricing", "2 x (3500.00 + 300.00) = 7600.00 KZT"],
], [285,210])
page()

title("4. Facade and Observer")
p("<b>Facade (Lecture 6).</b> RestaurantFacade exposes quote(), placeOrder(), getOrder(), listOrders(), updateStatus() and menu/subscription operations. placeOrder() coordinates construction, calculation and storage. Validation stays in request objects and the builder; price calculation stays in PriceCalculator; lifecycle handling stays in OrderService.")
p("<b>Observer (Lecture 8).</b> OrderService acts as the subject. It maintains subscribers through subscribe/unsubscribe and delivers OrderView events through OrderObserver.onOrderChanged(). KitchenObserver reacts to new orders. CustomerObserver collects status messages for its configured customer.")
d=Drawing(495,320)
box(d,10,225,215,75,"RestaurantFacade",["+ placeOrder(OrderRequest)","+ updateStatus(id, status)"])
box(d,285,225,200,75,"OrderService",["- orders: Map","- observers: Set<OrderObserver>"])
arrow(d,225,265,285,265,"uses")
box(d,10,95,210,75,"Order / PriceCalculator",["Construction and pricing"])
arrow(d,115,225,115,170,"uses")
box(d,275,95,220,75,"<<interface>> OrderObserver",["+ onOrderChanged(OrderView)"])
arrow(d,385,225,385,170,"uses","notifies")
box(d,240,0,125,50,"KitchenObserver")
box(d,375,0,120,50,"CustomerObserver")
arrow(d,302,50,345,95,"implements")
arrow(d,435,50,425,95,"implements")
diagram("facade-observer",d)
p("Synchronized service methods serialize state changes. Subscribers receive immutable snapshots. Duplicate subscriptions are deduplicated. Listener failures are logged and delivery continues. Recursive creation or status changes from a callback are rejected to preserve event order.", "SmallCustom")
page()

title("5. Integrated sequence")
p("All six patterns participate in the same placement flow. A later web controller can replace Demo as the caller of the facade.")
d=Drawing(495,475)
actors=[("Demo",35),("Facade",125),("Construction",245),("Pricing",355),("OrderService",455)]
for name,x in actors:
    box(d,x-35,425,70,40,name)
    d.add(Line(x,420,x,15,strokeColor=GRAY,strokeDashArray=[4,3],strokeWidth=.6))
steps=[
    (35,125,390,"placeOrder(request)"),
    (125,245,350,"createMeal()"),
    (245,125,315,"Meal"),
    (125,245,280,"wrap toppings; build()"),
    (245,125,245,"Order"),
    (125,355,210,"calculate(order)"),
    (355,125,175,"PriceBreakdown"),
    (125,455,140,"submit(order, price)"),
    (455,125,75,"OrderView"),
    (125,35,40,"OrderView"),
]
for a,b,y,label in steps:
    arrow(d,a,y,b,y,"uses")
    d.add(String(min(a,b)+4,y+7,label,fontName="Helvetica",fontSize=8,fillColor=INK))
d.add(Rect(285,94,205,28,fillColor=PALE,strokeColor=ACCENT,strokeWidth=.6))
d.add(String(292,105,"Store NEW; notify kitchen and customer",fontName="Helvetica",fontSize=8,fillColor=INK))
diagram("order-sequence",d)
p("The Construction lifeline is a compact view of MealCreator, the decorator objects and Order.Builder. The notification step calls each registered OrderObserver. Subsequent updateStatus calls publish PREPARING and READY snapshots.", "SmallCustom")
p("Status path: <b>NEW -&gt; PREPARING -&gt; READY</b>. A backward transition, missing order or null next status is rejected.")
page()

title("6. Demonstration evidence")
p("The screenshot below shows the actual standard output captured from <b>sh run.sh</b> and displayed in a local HTML transcript viewer. The viewer presents captured output; the executable application is restaurant.Demo.")
img=Image(str(DOCS/"screenshots/demo-output.png"))
ratio=img.imageHeight/img.imageWidth
img.drawWidth=495
img.drawHeight=495*ratio
story.append(img)
p("The output demonstrates both meal factories, stacked toppings, the two pricing strategies, a takeaway order, lifecycle notifications, rejection of a backward transition and a dine-in order.", "SmallCustom")
p("The original UTF-8 transcript is included as docs/demo-output.txt. Re-run the Java demo to reproduce it.", "SmallCustom")
page()

title("7. Verification and clean code")
p("The executable regression suite uses the Java standard library. It runs through RestaurantTests.main() and can be invoked with sh test.sh. Expected values are independent fixtures, including 3000.00, 2700.00 and the sequence of observer events.")
names=[line[6:] for line in (DOCS/"test-output.txt").read_text().splitlines() if line.startswith("PASS: ")]
for i,name in enumerate(names,1):
    p(str(i)+". "+escape(name),"SmallCustom")
p(f"<b>Verified result: {len(names)} scenarios passed.</b> Source was compiled with javac --release 17 on the available JDK 27 runtime. A native JDK 17 runtime was not available for execution.", "TextCustom")
p("Two warning messages in the test log are intentional. One subscriber throws a test exception. Another attempts a recursive status change. Both cases verify that the stored order remains consistent and later subscribers still receive the correct event.", "SmallCustom")
title("SOLID in the implementation")
table([
    ["Principle", "Concrete example"],
    ["Single Responsibility", "Demo displays; PriceCalculator prices; OrderService owns lifecycle."],
    ["Open/Closed", "New strategy and observer classes implement existing contracts."],
    ["Liskov Substitution", "Meals and their decorators are consumed through Meal."],
    ["Interface Segregation", "Meal and OrderObserver expose small, focused contracts."],
    ["Dependency Inversion", "Pricing depends on DiscountStrategy; delivery depends on OrderObserver."],
], [150,345])
page()

title("8. Selected Java source")
p("Complete sources are supplied in the ZIP. These excerpts show the main roles directly from the executable files.", "SmallCustom")
p("<b>Factory Method</b>", "TextCustom")
code(source("meal/MealCreator.java") + "\n" + source("meal/BurgerCreator.java").replace("package restaurant.meal;\n",""))
p("<b>Decorator</b>", "TextCustom")
code(source("meal/Cheese.java"))
p("<b>Builder usage</b>", "TextCustom")
code(method("RestaurantFacade.java","        Order.Builder builder =", "        for (ItemRequest item"))
p("buildOrder() adds every requested meal to this builder and returns builder.build(). The private Order constructor copies the completed contents.", "SmallCustom")
p("<b>Builder validation excerpt</b>", "TextCustom")
code(method("order/Order.java","            if (items.isEmpty())", "            if (serviceType == ServiceType.TAKEAWAY"))
page()

title("9. Selected Java source continued")
p("<b>Strategy context</b>", "TextCustom")
code(method("pricing/PriceCalculator.java","    public PriceBreakdown calculate","\n}"))
p("<b>Facade entry point</b>", "TextCustom")
code(method("RestaurantFacade.java","    public OrderView placeOrder","    public OrderView getOrder"))
p("<b>Observer delivery</b>", "TextCustom")
code(method("service/OrderService.java","    private void notifyObservers","    private void checkNotInCallback"))
p("The full service also contains the callback reentry check, valid-transition check, immutable snapshot creation and synchronized state access.", "SmallCustom")
page()

title("10. Conclusion")
p("The implementation coordinates six course patterns in a complete restaurant-ordering scenario. Factories create meals, decorators add toppings, a builder validates order contents, strategies calculate discounts, the facade coordinates the workflow and observers receive lifecycle events.")
p("Each pattern has a small set of named classes and a visible effect in the demonstration. The request/response boundary allows the business code to be reused by a future web presentation layer.")
title("Further work")
p("Add Java HTTP controllers that delegate to one shared RestaurantFacade instance. A React application can provide menu selection, a cart, a customer status screen and a kitchen screen. The proposed endpoint-to-method mapping and JSON example are included in docs/WEB_INTEGRATION.md.")
p("Persistent storage can retain orders across restarts. A real web deployment should identify users by authenticated IDs, authorize kitchen actions, verify discount eligibility and define idempotency for order placement. Network notifications should be delivered outside the synchronized service callback.")
title("Submission and individual defense")
p("The team should enter its group, confirm the proposed pattern allocation, document actual individual implementation and review the final report before Moodle submission. Each member should explain the interfaces, concrete classes, call sequence and one validation scenario in their assigned area.")
title("References")
for text in [
    "Final Project Defense Requirements.pdf, supplied course slides, pp. 1-2.",
    "Software Design Patterns (2).pdf, supplied syllabus, academic year 2026-2027, pp. 3-5.",
    "Lecture 1.pdf, Builder, pp. 7-12.",
    "Lecture 2.pdf, Factory Method and Abstract Factory, pp. 2-10.",
    "Lecture 5.pdf, Decorator, pp. 14-18.",
    "Lecture 6.pdf, Facade, pp. 24-26.",
    "Lecture 7.pdf, Strategy, pp. 6-7.",
    "Lecture 8.pdf, Observer, pp. 19-22.",
    "Lecture 10.pdf, SOLID and source control, pp. 20-36.",
]:
    p(escape(text),"SmallCustom")

def footer(canvas,doc):
    canvas.setStrokeColor(colors.HexColor("#d7e1e3"))
    canvas.line(50,40,545,40)
    canvas.setFont("Helvetica",8)
    canvas.setFillColor(GRAY)
    canvas.drawString(50,27,"Restaurant Ordering System  |  Software Design Patterns")
    canvas.drawRightString(545,27,str(doc.page))

SimpleDocTemplate(str(DOCS/"report.pdf"), pagesize=(595.28,841.89),
    leftMargin=50,rightMargin=50,topMargin=45,bottomMargin=55,
    title="Restaurant Ordering System - Final Project Report",
    author="Timur Naumov, Madi Talgat, Merey Kuatbay").build(story,onFirstPage=footer,onLaterPages=footer)
print(DOCS/"report.pdf")
