/*|----------------------------------------------------------------------------------------------
 *|														Heidelberg University
 *|	  _____ _____  _____      _                     	Department of Geography		
 *|	 / ____|_   _|/ ____|    (_)                    	Chair of GIScience
 *|	| |  __  | | | (___   ___ _  ___ _ __   ___ ___ 	(C) 2014-2016
 *|	| | |_ | | |  \___ \ / __| |/ _ \ '_ \ / __/ _ \	
 *|	| |__| |_| |_ ____) | (__| |  __/ | | | (_|  __/	Berliner Strasse 48								
 *|	 \_____|_____|_____/ \___|_|\___|_| |_|\___\___|	D-69120 Heidelberg, Germany	
 *|	        	                                       	http://www.giscience.uni-hd.de
 *|								
 *|----------------------------------------------------------------------------------------------*/
package heigit.ors.routing.util.extrainfobuilders;

import com.graphhopper.routing.util.SteepnessUtil;
import com.graphhopper.util.DistanceCalc3D;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

import heigit.ors.routing.RouteExtraInfo;
import heigit.ors.routing.RouteSegmentItem;

public class SteepnessExtraInfoBuilder extends RouteExtraInfoBuilder 
{
	private boolean _firstSegment = true;
	private double _x0, _y0, _z0, _x1, _y1, _z1;
	private double _elevDiff = 0;
	private double _cumElevation = 0.0;
	private double _maxAltitude = Double.MIN_VALUE;
	private double _minAltitude = Double.MAX_VALUE;
	private double _prevMinAltitude, _prevMaxAltitude;
	private double _splitLength = 0.0;
	private int _prevGradientCat = 0;
	private int _startIndex;
	private int _pointsCount = 0;
	private RouteSegmentItem _prevSegmentItem;
	private DistanceCalc3D _distCalc;
	
    public SteepnessExtraInfoBuilder(RouteExtraInfo extraInfo) 
    {
		super(extraInfo);
		_distCalc = Helper.DIST_3D;
	}

	public void addSegment(double value, int valueIndex, PointList geom, double dist, boolean lastEdge)
    {

    }
	
	public void addPoints(PointList geom)
	{
		int nPoints = geom.getSize() - 1;
		if (nPoints == 0)
			return;		
		
		if (_prevSegmentItem != null)
			_startIndex = _prevSegmentItem.getTo();

		int j0 = 0;
		
		if (_firstSegment)
		{
			j0 = 1;

			_x0 = geom.getLon(0);
			_y0 = geom.getLat(0);
			_z0 = geom.getEle(0);
			
			_maxAltitude = _z0;
			_minAltitude = _z0;
			_pointsCount++;
			
			_firstSegment = false;
		}
		
		for (int j = j0; j < nPoints; ++j) {
			_x1 = geom.getLon(j);
			_y1 = geom.getLat(j);
			_z1 = geom.getEle(j);
			
			_elevDiff = _z1 - _z0;
			_cumElevation += _elevDiff;
			double segLength = _distCalc.calcDist(_y0, _x0, _z0, _y1, _x1, _z1);

			_prevMinAltitude = _minAltitude;
			_prevMaxAltitude = _maxAltitude;
			if (_z1 > _maxAltitude)
				_maxAltitude = _z1;
			if (_z1 < _minAltitude)
				_minAltitude = _z1;

			//if ((_maxAltitude - _z1 > SteepnessUtil.ELEVATION_THRESHOLD || _z1 - _minAltitude > SteepnessUtil.ELEVATION_THRESHOLD) && _splitLength > 30)
			if ((_prevMaxAltitude - _z1 > SteepnessUtil.ELEVATION_THRESHOLD || _z1 - _prevMinAltitude > SteepnessUtil.ELEVATION_THRESHOLD) && _splitLength > 30)
			{
				boolean bApply = true;
				int elevSign = (_cumElevation -  _elevDiff) > 0 ? 1 : -1;
				double gradient = elevSign*100*(_prevMaxAltitude - _prevMinAltitude) / _splitLength;
				
				if (_prevGradientCat != 0 )
				{
					double zn= Double.MIN_NORMAL;
					
					if (j + 1 < nPoints)
					  zn = geom.getEle(j + 1);

					if (zn != Double.MIN_VALUE)
					{						
						double elevGap = segLength/30;
						if (elevSign > 0 /* && Math.Abs(prevSplit.Gradient - gradient) < gradientDiff)//*/ && _prevGradientCat > 0)
						{
							if (Math.abs(zn - _z1) < elevGap)
								bApply = false;
						}
						else if(/*Math.Abs(prevSplit.Gradient - gradient) < gradientDiff)//*/_prevGradientCat < 0)
						{
							if (Math.abs(zn - _z1) < elevGap)
								bApply = false;
						}
					}
				}
				
				if (bApply)
				{
					int gradientCat = SteepnessUtil.getCategory(gradient);
					int iEnd = _startIndex + _pointsCount;

					if (_prevGradientCat == gradientCat && _prevSegmentItem != null)
					{
						_prevSegmentItem.setTo(iEnd);
						_prevSegmentItem.setDistance(_prevSegmentItem.getDistance() + _splitLength);
					}
					else
					{
						RouteSegmentItem item = new RouteSegmentItem(_startIndex,  iEnd, gradientCat, _splitLength);
						_extraInfo.add(item);
						_prevSegmentItem = item;
					}
					
					_pointsCount = 0;
					_startIndex = iEnd;
					_prevGradientCat = gradientCat;
					_minAltitude = Math.min(_z0, _z1);
					_maxAltitude = Math.max(_z0, _z1);
					_splitLength = 0.0;
					
					_cumElevation = _elevDiff;
				}
			}
			
			_splitLength += segLength;
			
			_x0 = _x1;
			_y0 = _y1;
			_z0 = _z1;
			
			_pointsCount++;
		}
		
		if (_splitLength > 0)
		{
			_elevDiff = _maxAltitude - _minAltitude;
			if (_extraInfo.isEmpty() && _splitLength < 50 && _elevDiff < SteepnessUtil.ELEVATION_THRESHOLD)
				_elevDiff = 0;
			
			double gradient = (_cumElevation > 0 ? 1: -1)*100*_elevDiff / _splitLength;
			int gradientCat = SteepnessUtil.getCategory(gradient);
			if (_prevSegmentItem != null && (_prevGradientCat == gradientCat || _splitLength < 30))
			{
				_prevSegmentItem.setTo(_startIndex + _pointsCount);
			}
			else
			{
				RouteSegmentItem item = new RouteSegmentItem(_startIndex, _startIndex + _pointsCount, gradientCat, _splitLength);
				_extraInfo.add(item);
				_prevSegmentItem = item;
				_prevGradientCat = gradientCat;
				_pointsCount = 0;
			}
		}
	}
	
	public void finish()
	{
	}
}
