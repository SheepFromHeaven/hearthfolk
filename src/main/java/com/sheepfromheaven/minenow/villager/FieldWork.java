package com.sheepfromheaven.minenow.villager;

/**
 * The outdoor work of a trade (harvesting a field, felling a grove). While it runs the villager doesn't start crafting
 * at its workstation ({@link CraftAtWorkstation}): the hammering takes over its walking, and a farmer with wheat in its
 * pockets kept stopping mid-harvest to bake.
 */
public interface FieldWork {}
