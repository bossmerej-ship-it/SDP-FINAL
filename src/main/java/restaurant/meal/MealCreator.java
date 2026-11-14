package restaurant.meal;

// Subclasses decide which concrete Meal to create.
public abstract class MealCreator {
    public abstract Meal createMeal();
}
