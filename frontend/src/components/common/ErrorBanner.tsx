interface Props {
  message: string;
  onClose: () => void;
}

export default function ErrorBanner({ message, onClose }: Props) {
  return (
    <div className="error-banner">
      <span style={{ flex: 1 }}>⚠️ {message}</span>
      <button onClick={onClose}>✕</button>
    </div>
  );
}

