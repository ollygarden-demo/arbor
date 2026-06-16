package org.springframework.samples.petclinic.customers.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetTest {

    @Test
    void toStringShouldNotContainOwnerPii() {
        Owner owner = new Owner();
        owner.setFirstName("Jane");
        owner.setLastName("Smith");

        Pet pet = new Pet();
        pet.setId(42);
        pet.setName("Fluffy");
        owner.addPet(pet);

        String result = pet.toString();

        assertThat(result).doesNotContain("Jane");
        assertThat(result).doesNotContain("Smith");
    }
}
