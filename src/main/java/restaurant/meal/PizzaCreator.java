package restaurant.meal;

public final class PizzaCreator extends MealCreator {
    @Override
    public Meal createMeal() { return new Pizza(); }
}
