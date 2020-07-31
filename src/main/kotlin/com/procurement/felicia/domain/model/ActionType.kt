package com.procurement.felicia.domain.model

sealed class Action

class        NewClient                    : Action()
data class   Reassign(val client: Client) : Action()
