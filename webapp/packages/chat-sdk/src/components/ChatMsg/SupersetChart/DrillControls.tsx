import { Button } from 'antd';
import type { DrillDownDimensionType } from '../../../common/type';

type Props = {
  recommendedDimensions: DrillDownDimensionType[];
  drillPath: DrillDownDimensionType[];
  loading?: boolean;
  onDrillDown: (dimension: DrillDownDimensionType) => void;
  onDrillUp: () => void;
};

const sectionLabelStyle = {
  fontSize: 12,
  color: 'rgba(0, 0, 0, 0.65)',
} as const;

const pathTagStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  padding: '2px 8px',
  borderRadius: 999,
  background: '#ffffff',
  color: 'rgba(0, 0, 0, 0.75)',
  fontSize: 12,
  lineHeight: '20px',
} as const;

const DrillControls: React.FC<Props> = ({
  recommendedDimensions,
  drillPath,
  loading = false,
  onDrillDown,
  onDrillUp,
}) => {
  const hasControls = recommendedDimensions.length > 0 || drillPath.length > 0;

  if (!hasControls) {
    return null;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, width: '100%' }}>
      {recommendedDimensions.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={sectionLabelStyle}>推荐下钻维度</span>
          {recommendedDimensions.map(dimension => (
            <Button
              key={`${dimension.id}-${dimension.bizName}`}
              size="small"
              disabled={loading}
              onClick={() => onDrillDown(dimension)}
            >
              {dimension.name}
            </Button>
          ))}
        </div>
      )}
      {drillPath.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={sectionLabelStyle}>当前钻取路径</span>
          {drillPath.map((dimension, index) => (
            <span key={`${dimension.id}-${dimension.bizName}-${index}`} style={pathTagStyle}>
              {dimension.name}
            </span>
          ))}
          <Button size="small" disabled={loading} onClick={onDrillUp}>
            上钻
          </Button>
        </div>
      )}
    </div>
  );
};

export default DrillControls;
