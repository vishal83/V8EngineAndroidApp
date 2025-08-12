package com.visgupta.example.v8integrationandroidapp

data class BufferInfo(
    val name: String,
    val size: Int,
    val capacity: Int,
    val available: Int
) {
    val usagePercentage: Float
        get() = if (capacity > 0) (size.toFloat() / capacity.toFloat()) * 100f else 0f
    
    val isFull: Boolean
        get() = size >= capacity
    
    val isEmpty: Boolean
        get() = size == 0
        
    override fun toString(): String {
        return "BufferInfo(name='$name', size=$size, capacity=$capacity, available=$available, usage=${String.format("%.1f", usagePercentage)}%)"
    }
}
