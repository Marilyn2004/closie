export default function Icon({ name, ...props }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.4"
      width="20"
      height="20"
      aria-hidden="true"
      {...props}
    >
      {name === "search" ? (
        <>
          <circle cx="10.5" cy="10.5" r="6.5" />
          <path d="m16 16 5 5" />
        </>
      ) : name === "heart" ? (
        <path d="M20.7 5.7a5 5 0 0 0-7.1 0L12 7.3l-1.6-1.6a5 5 0 0 0-7.1 7.1L12 21l8.7-8.2a5 5 0 0 0 0-7.1Z" />
      ) : name === "spark" ? (
        <path d="m12 2 2.6 7.4L22 12l-7.4 2.6L12 22l-2.6-7.4L2 12l7.4-2.6Z" />
      ) : (
        <path d="M5 12h14m-6-6 6 6-6 6" />
      )}
    </svg>
  );
}
