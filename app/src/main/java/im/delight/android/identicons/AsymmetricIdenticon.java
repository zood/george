package im.delight.android.identicons;

/*
 * Copyright (c) delight.im <info@delight.im>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

public class AsymmetricIdenticon extends Identicon {

	public AsymmetricIdenticon(Context context) {
		super(context);
	}

	public AsymmetricIdenticon(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AsymmetricIdenticon(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected boolean isCellVisible(int row, int column) {
		return getByte(3 + row * getColumnCount() + column) >= 0;
	}

	@Override
	protected int getIconColor() {
		return Color.rgb(getByte(0)+128, getByte(1)+128, getByte(2)+128);
	}

	@Override
	protected int getRowCount() {
		return 4;
	}

	@Override
	protected int getColumnCount() {
		return 4;
	}

}
