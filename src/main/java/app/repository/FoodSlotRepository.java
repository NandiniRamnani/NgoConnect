package app.repository;

import app.model.FoodSlot;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Plain CRUD only. Date-range listing lives in FoodSlotService on MongoTemplate, because a derived
 * "findByDateBetween" is exclusive at both ends in Spring Data MongoDB and would silently drop
 * today's and the last day's slots.
 */
public interface FoodSlotRepository extends MongoRepository<FoodSlot, String> {
}
