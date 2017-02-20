package io.pijun.george.event;

import java.util.ArrayList;

import io.pijun.george.models.MovementType;

public class MovementsUpdated {

    public final ArrayList<MovementType> movements;

    public MovementsUpdated(ArrayList<MovementType> m) {
        this.movements = m;
    }

}
