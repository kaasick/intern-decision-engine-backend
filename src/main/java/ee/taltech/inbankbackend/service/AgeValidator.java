package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.exception.PersonalCodeException;
import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeParser;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;

import java.time.LocalDate;
import java.time.Period;

public class AgeValidator {
    // An arbitrary life expectancy
    private final int lifeExpectancy = 80;
    // Underage variable
    private final int minAge = 18;

    private static final EstonianPersonalCodeParser personalCodeParser = new EstonianPersonalCodeParser();

    public static LocalDate getBirthDate(String personalCode) throws InvalidPersonalCodeException, PersonalCodeException {
        LocalDate birthDate = personalCodeParser.getDateOfBirth(personalCode);
        Period age = personalCodeParser.getAge(personalCode);
        return birthDate;
    }

    public static Period getAge(String personalCode) throws PersonalCodeException {
        Period age = personalCodeParser.getAge(personalCode);
        return age;
    }
}
