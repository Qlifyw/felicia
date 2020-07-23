package com.procurement.felicia.domain.errors

import com.procurement.felicia.domain.DataValidationError

class MissingRequiredAttribute(name: String) :
    DataValidationError(description = "Missing required attribute '$name'.", name = name)

class DataTypeMismatch(name: String, expectedType: String, actualType: String) :
    DataValidationError(
        description = "Data type mismatch of attribute '$name'. Expected data type: '$expectedType', actual data type: '$actualType'.",
        name = name
    )

class UnknownValue(name: String, expectedValues: Collection<String>, actualValue: String) :
    DataValidationError(
        description = "Attribute value mismatch of '$name' with one of enum expected values. Expected values: '${expectedValues.joinToString()}', actual value: '$actualValue'.",
        name = name
    )

class DataFormatMismatch(name: String, expectedFormat: String, actualValue: String) :
    DataValidationError(
        description = "Data format mismatch of attribute '$name'. Expected data format: '$expectedFormat', actual value: '$actualValue'.",
        name = name
    )

class DataMismatchToPattern(name: String, pattern: String, actualValue: String) :
    DataValidationError(
        description = "Data mismatch of attribute '$name' to the pattern: '$pattern'. Actual value: '$actualValue'.",
        name = name
    )

class UniquenessDataMismatch(name: String, value: String) :
    DataValidationError(
        description = "Uniqueness data mismatch of attribute '$name': '$value'.",
        name = name
    )

class InvalidNumberOfElementsInArray(name: String, min: Int? = null, max: Int? = null, actualLength: Int) :
    DataValidationError(
        description = "Invalid number of objects in the array of attribute '$name'. Expected length from '${min ?: "none min"}' to '${max ?: "none max"}', actual length: '$actualLength'.",
        name = name
    )

class InvalidStringLength(name: String, min: Int? = null, max: Int? = null, actualLength: Int) :
    DataValidationError(
        description = "Invalid number of chars in string of attribute '$name'. Expected length from '${min ?: "none min"}' to '${max ?: "none max"}', actual length: '$actualLength'.",
        name = name
    )

class EmptyObject(name: String) :
    DataValidationError(description = "Attribute '$name' is an empty object.", name = name)

class EmptyArray(name: String) :
    DataValidationError(description = "Attribute '$name' is an empty array.", name = name)

class EmptyString(name: String) :
    DataValidationError(description = "Attribute '$name' is an empty string.", name = name)

class UnexpectedAttribute(name: String) :
    DataValidationError(description = "Unexpected attribute '$name'.", name = name)