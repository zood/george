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

import java.security.MessageDigest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

abstract public class Identicon extends View {
	
	private static final String HASH_ALGORITHM = "SHA-256";
	private final int mRowCount;
	private final int mColumnCount;
	private final Paint mPaint;
	private volatile int mCellWidth;
	private volatile int mCellHeight;
	private volatile byte[] mHash;
	private volatile int[][] mColors;
	private volatile boolean mReady;

	public Identicon(Context context) {
		super(context);

		mRowCount = getRowCount();
		mColumnCount = getColumnCount();
		mPaint = new Paint();

		init();
	}

	public Identicon(Context context, AttributeSet attrs) {
		super(context, attrs);

		mRowCount = getRowCount();
		mColumnCount = getColumnCount();
		mPaint = new Paint();

		init();
	}

	public Identicon(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		mRowCount = getRowCount();
		mColumnCount = getColumnCount();
		mPaint = new Paint();

		init();
	}
		
	@SuppressLint("NewApi")
	protected void init() {
		mPaint.setStyle(Paint.Style.FILL);
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		
		setWillNotDraw(false);
		if (Build.VERSION.SDK_INT >= 11) {
			setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
	}

	public void show(String input) {
		// if the input was null
		if (input == null) {
			// we can't create a hash value and have nothing to show (draw to the view)
			mHash = null;
		}
		// if the input was a proper string (non-null)
		else {
			// generate a hash from the string to get unique but deterministic byte values 
			try {
				final MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
				digest.update(input == null ? new byte[0] : input.getBytes());
				mHash = digest.digest();
			}
			catch (Exception e) {
				mHash = null;
			}
		}
		
		// set up the cell colors according to the input that was provided via show(...)
		setupColors();
		
		// this view may now be drawn (and thus must be re-drawn)
		mReady = true;
		invalidate();
	}
	
	public void show(int input) {
		show(String.valueOf(input));
	}
	
	public void show(long input) {
		show(String.valueOf(input));
	}
	
	public void show(float input) {
		show(String.valueOf(input));
	}
	
	public void show(double input) {
		show(String.valueOf(input));
	}
	
	public void show(byte input) {
		show(String.valueOf(input));
	}
	
	public void show(char input) {
		show(String.valueOf(input));
	}
	
	public void show(boolean input) {
		show(String.valueOf(input));
	}
	
	public void show(Object input) {
		if (input == null) {
			mHash = null;
		}
		else {
			show(String.valueOf(input));
		}
	}
	
	protected void setupColors() {
		mColors = new int[mRowCount][mColumnCount];
		int colorVisible = getIconColor();

		for (int r = 0; r < mRowCount; r++) {
			for (int c = 0; c < mColumnCount; c++) {
				if (isCellVisible(r, c)) {
					mColors[r][c] = colorVisible;
				}
				else {
					mColors[r][c] = Color.TRANSPARENT;
				}
			}
		}
	}
	
	protected byte getByte(int index) {
		if (mHash == null) {
			return -128;
		}
		else {
			return mHash[index % mHash.length];
		}
	}
	
	abstract protected int getRowCount();
	
	abstract protected int getColumnCount();

	abstract protected boolean isCellVisible(int row, int column);

	abstract protected int getIconColor();
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		mCellWidth = w / mColumnCount;
		mCellHeight = h / mRowCount;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
		setMeasuredDimension(size, size);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (mReady) {
			int x, y;
			for (int r = 0; r < mRowCount; r++) {
				for (int c = 0; c < mColumnCount; c++) {
					x = mCellWidth * c;
					y = mCellHeight * r;
					
					mPaint.setColor(mColors[r][c]);

					canvas.drawRect(x, y + mCellHeight, x + mCellWidth, y, mPaint);
				}
			}
		}
	}

}
