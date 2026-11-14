package restaurant.meal;

public abstract class MealDecorator implements Meal {
    protected final Meal meal;

    protected MealDecorator(Meal meal) {
        if (meal == null) {
            throw new IllegalArgumentException("A topping requires a meal.");
        }
        this.meal = meal;
    }
}
