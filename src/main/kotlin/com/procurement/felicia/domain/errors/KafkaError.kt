package com.procurement.felicia.domain.errors

import com.procurement.felicia.domain.KafkaError

class ConsumerConflict: KafkaError(
    description = "Subscribing cannot be completed since the current group has already rebalanced and assigned the partitions to another member."
)