package restaurant.meal;

public final class BurgerCreator extends MealCreator {
    @Override
    public Meal createMeal() { return new Burger(); }
}
