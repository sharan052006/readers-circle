import ReaderLayout from '../components/ReaderLayout';
import RecommendationsView from '../components/Recommendations';

export default function ReaderRecommendations() {
  return (
    <ReaderLayout active="recommend">
      <div style={{ padding: '18px 22px 40px' }}>
        <RecommendationsView />
      </div>
    </ReaderLayout>
  );
}
