package com.example

import com.example.data.CartItem
import com.example.data.MenuCategory
import com.example.data.MenuItem
import com.example.data.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun cartSubtotalAndTotal_calculatedCorrectly() {
    val burger = MenuItem(id = "chicken_burger", name = "Chicken Burger", price = 180L)
    val coke = MenuItem(id = "coke", name = "Coke", price = 50L)

    val cart = listOf(
      CartItem(menuItem = burger, quantity = 2),
      CartItem(menuItem = coke, quantity = 1)
    )

    assertEquals(360L, cart[0].subtotal)
    assertEquals(50L, cart[1].subtotal)
    assertEquals(410L, cart.sumOf { it.subtotal })
  }

  @Test
  fun orderStatus_progressesThroughExpectedStages() {
    assertEquals(OrderStatus.CONFIRMED, OrderStatus.PENDING.nextStatus())
    assertEquals(OrderStatus.PREPARING, OrderStatus.CONFIRMED.nextStatus())
    assertEquals(OrderStatus.READY, OrderStatus.PREPARING.nextStatus())
    assertEquals(OrderStatus.DELIVERED, OrderStatus.READY.nextStatus())
    assertNull(OrderStatus.DELIVERED.nextStatus())
  }

  @Test
  fun enumParsing_handlesUnknownValuesSafely() {
    assertEquals(MenuCategory.SIDES, MenuCategory.fromRaw("UnknownCategory"))
    assertEquals(OrderStatus.PENDING, OrderStatus.fromRaw("UnknownStatus"))
  }
}
