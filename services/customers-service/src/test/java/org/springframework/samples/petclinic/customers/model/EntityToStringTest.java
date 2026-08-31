package org.springframework.samples.petclinic.customers.model;

import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner and Pet end up in log messages and diagnostics, so their string
 * representations must expose opaque identifiers only - never contact details.
 */
class EntityToStringTest {

    @Test
    void ownerToStringShouldNotExposePersonalData() {
        Owner owner = setupOwner();

        assertThat(owner.toString())
            .doesNotContain("George", "Bush", "Rainbow Road 1", "Springfield", "0123456789");
    }

    @Test
    void petToStringShouldNotExposeOwnerPersonalData() {
        Owner owner = setupOwner();
        Pet pet = new Pet();
        pet.setId(2);
        pet.setName("Basil");
        pet.setBirthDate(new Date());
        PetType petType = new PetType();
        petType.setId(6);
        petType.setName("hamster");
        pet.setType(petType);
        owner.addPet(pet);

        assertThat(pet.toString())
            .contains("id = 2")
            .doesNotContain("George", "Bush", "Basil");
    }

    @Test
    void petToStringShouldWorkWithoutTypeAndOwner() {
        Pet pet = new Pet();
        pet.setId(3);

        assertThat(pet.toString()).contains("id = 3");
    }

    private Owner setupOwner() {
        Owner owner = new Owner();
        owner.setFirstName("George");
        owner.setLastName("Bush");
        owner.setAddress("Rainbow Road 1");
        owner.setCity("Springfield");
        owner.setTelephone("0123456789");
        return owner;
    }
}
